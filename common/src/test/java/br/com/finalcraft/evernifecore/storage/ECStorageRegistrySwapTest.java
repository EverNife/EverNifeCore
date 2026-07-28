package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens to a plugin-owned {@link ECStorage} when a reload replaces the per-plugin
 * {@code RefRegistry} under it.
 *
 * <p>Every reload builds a fresh {@code ECRegistries}, so a handle opened before it keeps a registry
 * the plugin's freshly rebound PDSections no longer resolve through. These pin the contract that
 * replaced the old silence: the handle is marked detached, refuses to hand out NEW wiring, and still
 * lets the plugin flush and close what it already had.</p>
 */
@ECoreTest
class ECStorageRegistrySwapTest {

    private static final String PLUGIN_NAME = "SwapTestPlugin";

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        //the ECPluginData cache is static and keyed by name: a stale one would point at a @TempDir
        //that no longer exists and reach the next test in this JVM
        ECPluginManager.removePluginData(PLUGIN_NAME);
    }

    @Test
    void aReloadDetachesAHandleNothingReopened() throws IOException {
        File storageYml = writeStorageYml("swap_detach");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ECStorage handle = ECStorage.open(plugin, pluginStorageSection("detach"),
                BackendDefinition.memory()).join();
        assertFalse(handle.isDetached(), "a freshly opened handle is wired to the live registry");

        PlayerController.initialize(storageYml); // the reload: fresh ECRegistries, fresh per-plugin child
        assertTrue(handle.isDetached(),
                "the registry this handle holds was replaced - it must know it is no longer wired");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> handle.manager(descriptor(), CachePolicy.always()));
        assertTrue(refused.getMessage().contains("onStorageReload"),
                "the refusal must name the fix, not just the symptom: " + refused.getMessage());

        handle.close().join();
    }

    @Test
    void aDetachedHandleStillFlushesAndCloses() throws IOException {
        File storageYml = writeStorageYml("swap_flush");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ECStorage handle = ECStorage.open(plugin, pluginStorageSection("flush"),
                BackendDefinition.memory()).join();
        handle.manager(descriptor(), CachePolicy.always());

        PlayerController.initialize(storageYml);
        assertTrue(handle.isDetached());

        //the plugin's only window to persist what was dirty before it re-opens: refusing these would
        //answer a resolution bug with data loss
        assertDoesNotThrow(() -> handle.flushManagers().join(), "a detached handle must still flush");
        assertDoesNotThrow(() -> handle.close().join(), "a detached handle must still close");
    }

    @Test
    void reopeningADetachedHandleKeepsTheLiveConnection() throws IOException {
        File storageYml = writeStorageYml("swap_rebind");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ConfigSection section = pluginStorageSection("rebind");
        ECStorage handle = ECStorage.open(plugin, section, BackendDefinition.memory()).join();
        Object connectionBefore = handle.storage();
        CachingManager<UUID, Shop> managerBefore = handle.manager(descriptor(), CachePolicy.always());

        PlayerController.initialize(storageYml);
        assertTrue(handle.isDetached());

        ECStorage reopened = ECStorage.openOrReload(plugin, section, handle).join();

        assertSame(handle, reopened, "the same target reuses the handle instead of opening a new one");
        assertFalse(reopened.isDetached(), "re-opening clears the detached mark");
        assertSame(connectionBefore, reopened.storage(),
                "a swapped registry must NOT tear down the connection - only the registry moved");
        assertNotSame(managerBefore, reopened.manager(descriptor(), CachePolicy.always()),
                "the managers are re-derived, so they register in the live registry");
    }

    @Test
    void aPluginLessHandleIsNeverDetached() throws IOException {
        File storageYml = writeStorageYml("swap_pluginless");
        PlayerController.initialize(storageYml);

        //no ECPluginData: a private registry is this handle's contract, not an accident of boot order
        ECStorage handle = ECStorage.open(BackendDefinition.memory()).join();
        PlayerController.initialize(storageYml);

        assertFalse(handle.isDetached(), "a plugin-less handle owns its registry and nothing replaced it");
        assertDoesNotThrow(() -> handle.manager(descriptor(), CachePolicy.always()));
        handle.close().join();
    }

    @Test
    void aClosedHandleIsNotTrackedThroughAReload() throws IOException {
        File storageYml = writeStorageYml("swap_closed");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ECStorage handle = ECStorage.open(plugin, pluginStorageSection("closed"),
                BackendDefinition.memory()).join();
        handle.close().join();

        assertDoesNotThrow(() -> PlayerController.initialize(storageYml),
                "a reload must not trip over a handle that was already closed");
        assertFalse(handle.isDetached(), "a closed handle is out of the tracking - nothing to detach");
    }

    @Test
    void aManagerHandedOutBeforeTheReloadKeepsWorking() throws IOException {
        File storageYml = writeStorageYml("swap_old_manager");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ECStorage handle = ECStorage.open(plugin, pluginStorageSection("old_manager"),
                BackendDefinition.memory()).join();
        CachingManager<UUID, Shop> manager = handle.manager(descriptor(), CachePolicy.always());
        UUID shopId = UUID.randomUUID();
        manager.saveAndCache(new Shop(shopId, "before")).join();

        PlayerController.initialize(storageYml);

        //the replaced registry keeps its entries on purpose: a manager the plugin already holds must
        //keep resolving inside that older but self-consistent graph, not suddenly resolve nothing
        assertTrue(manager.resolve(shopId).join().isPresent(),
                "a manager handed out before the reload must still serve what it stored");

        handle.close().join();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    public static class Shop {
        private UUID id;
        private String name;

        public Shop() {
        }

        public Shop(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static EntityDescriptor<UUID, Shop> descriptor() {
        return EntityDescriptor.builder(UUID.class, Shop.class)
                .collection("swap_shops")
                .keyExtractor(Shop::getId)
                .codec(new JacksonJsonCodec<>(Shop.class))
                .build();
    }

    /** An empty {@code storage:} block the plugin-aware open seeds with whatever definition it is given. */
    private ConfigSection pluginStorageSection(String name) {
        Config config = ConfigFactory.open((ECPluginData) null, tempDir.resolve(name + ".yml").toFile());
        return config.getConfigSection("storage");
    }

    private ECPluginData fakePluginData() {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(PLUGIN_NAME, tempDir.resolve(PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new FakePlugin());
    }

    /** Stands in for the platform's plugin object (a JavaPlugin on Bukkit); only its identity matters. */
    public static final class FakePlugin {
    }

    private File writeStorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "multi-platform-accounts:",
                "  enabled: true",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
