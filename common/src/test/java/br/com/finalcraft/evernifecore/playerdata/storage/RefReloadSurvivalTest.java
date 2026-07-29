package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code Ref} inside a PDSection that resolves an entity of a plugin-owned {@link ECStorage} keeps
 * resolving AFTER a core storage reload. A reload builds a fresh set of per-plugin reference registries;
 * the PDSection side rebinds to the fresh registry on its own, and the plugin re-opens its ECStorage in
 * a {@link PlayerController#onStorageReload(ECPluginData, java.lang.Runnable)} callback so its entity
 * manager reconnects into that same fresh registry.
 *
 * <ul>
 *   <li><b>survival</b> - the end-to-end scenario: a section {@code Ref} resolves an entity before AND
 *       after a real {@code bootstrap}-to-{@code bootstrap} reload;</li>
 *   <li><b>atomicity</b> - a reload whose fresh instance fails to construct never swaps the live instance
 *       nor fires the callbacks (they fire only post-swap);</li>
 *   <li><b>cleanup</b> - a reload fires the callback once, and unregistering the plugin drops it.</li>
 * </ul>
 */
@ECoreTest
class RefReloadSurvivalTest {

    private static final String PLUGIN_NAME = "RefReloadTestPlugin";


    @TempDir
    Path tempDir;

    private ECPluginData plugin;

    @BeforeEach
    void clearShutdowns(TestPlatform platform) {
        //a leftover request from an unrelated test class sharing this JVM must not be mistaken for
        //one fired by the reload below - see aFailedReloadKeepsTheLiveInstanceAndDoesNotFireCallbacks
        platform.reset();
    }

    @AfterEach
    void teardown(TestPlatform platform) {
        if (plugin != null) {
            PlayerController.unregisterPDSections(plugin); //also drops this plugin's reload callbacks
        }
        PlayerDataWorld.tearDown();
        ECPluginManager.removePluginData(PLUGIN_NAME);
        plugin = null;
        platform.reset(); //the atomicity test below now goes through the boot guard
    }

    // ==================================================================
    //  survival: the Ref resolves after a real core storage reload
    // ==================================================================

    @Test
    void refInPdSectionResolvesAfterAStorageReload() throws Exception {
        plugin = realPluginData();

        //core PlayerData on a persistent H2 mem (survives the reload's flush + reopen)
        File storageYml = Storages.h2("ref_reload_pd").writeTo(tempDir);
        PlayerController.initialize(storageYml);

        //the plugin registers its Ref-carrying PDSection, OWNED by the plugin -> per-plugin shared registry
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(plugin, ProfileSection.class, "profilesection").build());

        //the plugin opens its OWN ECStorage holding the Guild, on a SEPARATE persistent H2 mem; its Guild
        //manager registers in the plugin's shared registry (the same one the section resolves through)
        ConfigSection guildSection = inlineH2Section("ref_reload_guild");
        AtomicReference<ECStorage> guildStorage =
                new AtomicReference<>(ECStorage.openOrReload(plugin, guildSection, null).join());
        registerGuildManager(guildStorage.get());

        //the reload callback: re-open the ECStorage (reconnect into the fresh registry) and re-derive
        PlayerController.onStorageReload(plugin, () -> {
            ECStorage reopened = ECStorage.openOrReload(plugin, guildSection, guildStorage.get()).join();
            guildStorage.set(reopened);
            registerGuildManager(reopened); //re-register the Guild manager in the fresh registry
        });

        //store a Guild and a player profile whose ref points at it
        UUID gid = UUID.randomUUID();
        registerGuildManager(guildStorage.get()).saveAndCache(new Guild(gid, "Alpha")).join();

        UUID pid = UUID.randomUUID();
        PlayerController.handleLogin(pid, "Owner").join();
        ProfileSection profile = PlayerController.getLoaded(pid).getPDSection(ProfileSection.class).join();
        profile.guildRef = guildStorage.get().refRegistry().ref(gid, Guild.class);
        profile.markDirty();
        PlayerController.get().flushAll().join();

        //sanity BEFORE the reload: the ref resolves
        assertResolvesTo(pid, gid, "Alpha");

        // ---- reload the core storage (fresh instance, fresh per-plugin registry) ----
        PlayerController.initialize(storageYml);

        //re-read the profile fresh from the backend (its codec is now bound to the fresh registry) and
        //resolve: the callback reconnected the Guild manager into that same fresh registry
        PlayerController.clearPDSections(ProfileSection.class);
        assertResolvesTo(pid, gid, "Alpha");

        guildStorage.get().close().join();
    }

    // ==================================================================
    //  atomicity: a failed reload does not swap the instance nor fire callbacks
    // ==================================================================

    @Test
    void aFailedReloadKeepsTheLiveInstanceAndDoesNotFireCallbacks(TestPlatform platform) throws Exception {
        plugin = realPluginData();
        PlayerController.initialize(Storages.h2("atomic_live").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(plugin, ProfileSection.class, "profilesection").build());
        PlayerController live = PlayerController.get();

        AtomicInteger callbackFires = new AtomicInteger();
        PlayerController.onStorageReload(plugin, callbackFires::incrementAndGet);

        //a storage.yml whose backend refuses to init (H2 file, IFEXISTS on a db that never existed):
        //the fresh constructor fails before the swap, so the live instance is untouched
        File broken = writeBrokenStorageYml();
        assertThrows(Throwable.class, () -> PlayerController.initialize(broken),
                "a reload whose fresh instance fails to construct must propagate");

        assertSame(live, PlayerController.get(), "a failed reload must NOT swap the live instance");
        assertEquals(0, callbackFires.get(),
                "callbacks fire only after the swap - a pre-swap failure must fire none");
        assertTrue(platform.getShutdownReasons().isEmpty(),
                "a failed RELOAD must never stop the server - the live instance is still serving");
    }

    // ==================================================================
    //  cleanup: the callback fires on reload, and unregister drops it
    // ==================================================================

    @Test
    void unregisterPdSectionsDropsThePluginsReloadCallback() throws Exception {
        plugin = realPluginData();
        File storageYml = Storages.h2("hook_cleanup").writeTo(tempDir);
        PlayerController.initialize(storageYml);
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(plugin, ProfileSection.class, "profilesection").build());

        AtomicInteger fires = new AtomicInteger();
        PlayerController.onStorageReload(plugin, fires::incrementAndGet);

        //a reload fires it once (proves the mechanism runs post-swap)
        PlayerController.initialize(storageYml);
        assertEquals(1, fires.get(), "a reload must fire the storage-reload callback");

        //after the plugin disables its callback is gone: a further reload does not fire it
        PlayerController.unregisterPDSections(plugin);
        PlayerController.initialize(storageYml);
        assertEquals(1, fires.get(), "unregisterPDSections must drop the plugin's reload callback");
    }

    // ==================================================================
    //  helpers
    // ==================================================================

    /** Reads the section fresh (cache cleared upstream in the survival test) and asserts the ref resolves. */
    private void assertResolvesTo(UUID pid, UUID gid, String name) {
        ProfileSection section = PlayerController.getPDSection(pid, ProfileSection.class).join();
        Optional<Guild> resolved = section.guildRef.resolve().join();
        assertTrue(resolved.isPresent(), "the Ref must resolve the guild stored in the plugin's ECStorage");
        assertEquals(gid, resolved.get().id);
        assertEquals(name, resolved.get().name);
    }

    /** Derives (memoized in the handle) the Guild manager on {@code storage}, registered in its shared registry. */
    private CachingManager<UUID, Guild> registerGuildManager(ECStorage storage) {
        EntityDescriptor<UUID, Guild> descriptor = EntityDescriptor.builder(UUID.class, Guild.class)
                .collection("guilds")
                .keyExtractor(g -> g.id)
                .codec(storage.defaultCodec(Guild.class))
                .build();
        return storage.manager(descriptor, CachePolicy.always());
    }


    /** An H2 FILE backend with IFEXISTS on a db that was never created: init() fails deterministically, offline. */
    private File writeBrokenStorageYml() throws IOException {
        String missingDb = tempDir.resolve("never_created_db").toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:file:" + missingDb + ";IFEXISTS=TRUE\"",
                "default-backend: test_h2",
                "network:",
                "  storage-backend-id: test_h2",
                "");
        File file = tempDir.resolve("storage_broken.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** The plugin's inline single-backend section (child key IS the backend type), on a persistent H2 mem. */
    private ConfigSection inlineH2Section(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage:",
                "  h2:",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "");
        File file = tempDir.resolve("guild_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return ConfigFactory.open(file).getConfigSection("storage");
    }

    /** A real {@link ECPluginData}, built the way production does - through the plugin extractor. */
    private ECPluginData realPluginData() {
        Object javaPlugin = new FakePlugin();
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(PLUGIN_NAME, tempDir.resolve(PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(javaPlugin);
    }

    // ==================================================================
    //  fixtures
    // ==================================================================

    /** The entity the ref points at - a plain POJO in its own collection. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Guild {
        public UUID id;
        public String name;

        public Guild() {
        }

        Guild(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** A per-player section holding a ref into the guild collection - no custom codec, no lifecycle. */
    public static class ProfileSection extends PDSection {
        public Ref<UUID, Guild> guildRef;
    }

    /** Stands in for the platform's plugin object; only its identity matters. */
    public static final class FakePlugin {
    }


}
