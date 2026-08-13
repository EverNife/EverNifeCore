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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plugin-owned {@link ECStorage} across a core storage reload: the per-plugin {@code RefRegistry}
 * keeps its identity (a reload swaps its content, never the object), so the handle opened on enable
 * never detaches, never needs a callback, and keeps vending managers and serving data straight
 * through the reload.
 */
@ECoreTest
class ECStorageStableRegistryTest {

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
    void aHandleOpenedOnEnableWorksStraightThroughACoreReload() throws IOException {
        File storageYml = writeStorageYml("stable_handle");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ECStorage handle = ECStorage.open(plugin, pluginStorageSection("stable"),
                BackendDefinition.memory()).join();
        Object registryBefore = handle.refRegistry();
        assertSame(ECStorageRegistries.of(plugin), registryBefore,
                "a post-bootstrap open wires straight into the plugin's shared registry");

        CachingManager<UUID, Shop> manager = handle.manager(descriptor(), CachePolicy.always());
        UUID shopId = UUID.randomUUID();
        manager.saveAndCache(new Shop(shopId, "before")).join();

        PlayerController.initialize(storageYml); //the core reload - the plugin does NOTHING

        assertSame(registryBefore, handle.refRegistry(),
                "the shared registry kept its identity, so the handle is still correctly wired");
        assertSame(manager, handle.manager(descriptor(), CachePolicy.always()),
                "the handle keeps vending the same (memoized) manager - nothing detached");
        assertTrue(manager.resolve(shopId).join().isPresent(),
                "a manager handed out before the reload keeps serving its data");

        handle.close().join();
    }

    @Test
    void aPluginLessHandleIsUntouchedByAReload() throws IOException {
        File storageYml = writeStorageYml("stable_pluginless");
        PlayerController.initialize(storageYml);

        //no ECPluginData: a private registry is this handle's contract
        ECStorage handle = ECStorage.open(BackendDefinition.memory()).join();
        PlayerController.initialize(storageYml);

        assertDoesNotThrow(() -> handle.manager(descriptor(), CachePolicy.always()),
                "a private-registry handle has nothing to do with the core's reload");
        handle.close().join();
    }

    @Test
    void aClosedHandleDoesNotTripAReload() throws IOException {
        File storageYml = writeStorageYml("stable_closed");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ECStorage handle = ECStorage.open(plugin, pluginStorageSection("closed"),
                BackendDefinition.memory()).join();
        handle.close().join();

        assertDoesNotThrow(() -> PlayerController.initialize(storageYml),
                "a reload must not trip over a handle that was already closed");
    }

    @Test
    void openOrReloadOnTheSameTargetStillReusesTheConnectionAfterACoreReload() throws IOException {
        File storageYml = writeStorageYml("stable_reuse");
        PlayerController.initialize(storageYml);
        ECPluginData plugin = fakePluginData();

        ConfigSection section = pluginStorageSection("reuse");
        ECStorage handle = ECStorage.open(plugin, section, BackendDefinition.memory()).join();
        Object connectionBefore = handle.storage();
        CachingManager<UUID, Shop> managerBefore = handle.manager(descriptor(), CachePolicy.always());

        PlayerController.initialize(storageYml); //the core reload

        //the plugin's OWN config reload path (openOrReload) still behaves: same target = same
        //connection, caches wiped, managers re-derived on the same stable registry
        ECStorage reopened = ECStorage.openOrReload(plugin, section, handle).join();
        assertSame(handle, reopened, "the same target reuses the handle instead of opening a new one");
        assertSame(connectionBefore, reopened.storage(), "the live connection is kept");
        assertNotSame(managerBefore, reopened.manager(descriptor(), CachePolicy.always()),
                "openOrReload resets the handle, so its managers are re-derived");

        reopened.close().join();
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
                "network:",
                "  storage-backend-id: test_h2",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
