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
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.ECStorageRegistries;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code Ref} inside a PDSection that resolves an entity of a plugin-owned {@link ECStorage} keeps
 * resolving AFTER a core storage reload - with <b>no callback and no re-open</b>. The per-plugin
 * reference registries are stable in identity: a reload replaces their content (the core's managers),
 * never the objects, so the plugin's handle, its managers and every live {@code Ref} stay wired.
 *
 * <ul>
 *   <li><b>survival</b> - the end-to-end scenario: a section {@code Ref} (both a live instance from
 *       before the reload and one deserialized after it) resolves the plugin's entity across a real
 *       {@code initialize}-to-{@code initialize} reload, with nothing registered anywhere;</li>
 *   <li><b>identity</b> - the per-plugin registry and the global are the same objects across the
 *       reload;</li>
 *   <li><b>atomicity</b> - a reload whose fresh instance fails to construct keeps the live instance
 *       serving AND puts its managers back as the registries' resolvers.</li>
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
        platform.reset();
    }

    @AfterEach
    void teardown(TestPlatform platform) {
        if (plugin != null) {
            PlayerController.unregisterPDSections(plugin);
        }
        PlayerDataWorld.tearDown();
        ECPluginManager.removePluginData(PLUGIN_NAME);
        plugin = null;
        platform.reset(); //the atomicity test below goes through the boot guard
    }

    // ==================================================================
    //  survival: the Ref resolves across a core reload, no callback anywhere
    // ==================================================================

    @Test
    void refInPdSectionResolvesAfterAStorageReload_withNoCallback() throws Exception {
        plugin = realPluginData();

        //core PlayerData on a persistent H2 mem (survives the reload's flush + reopen)
        File storageYml = Storages.h2("ref_reload_pd").writeTo(tempDir);
        PlayerController.initialize(storageYml);

        //the plugin registers its Ref-carrying PDSection, OWNED by the plugin -> per-plugin shared registry
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(plugin, ProfileSection.class, "profilesection").build());

        //the plugin opens its OWN ECStorage holding the Guild, ONCE, on a SEPARATE persistent H2 mem; its
        //Guild manager registers in the plugin's shared registry (the same one the section resolves through)
        ConfigSection guildSection = inlineH2Section("ref_reload_guild");
        ECStorage guildStorage = ECStorage.openOrReload(plugin, guildSection, null).join();
        RefRegistry registryBefore = guildStorage.refRegistry();

        //store a Guild and a player profile whose ref points at it
        UUID gid = UUID.randomUUID();
        guildManager(guildStorage).saveAndCache(new Guild(gid, "Alpha")).join();

        UUID pid = UUID.randomUUID();
        PlayerController.handleLogin(pid, "Owner").join();
        ProfileSection profile = PlayerController.getLoaded(pid).getPDSection(ProfileSection.class).join();
        profile.guildRef = guildStorage.refRegistry().ref(gid, Guild.class);
        profile.markDirty();
        PlayerController.get().flushAll().join();

        //sanity BEFORE the reload, and keep the LIVE ref instance - never rebound
        Ref<UUID, Guild> liveRef = profile.guildRef;
        assertResolvesTo(pid, gid, "Alpha");

        // ---- reload the core storage: same registries, new content; the plugin does NOTHING ----
        PlayerController.initialize(storageYml);

        //the live Ref instance from before the reload still resolves (the plugin's manager was never
        //replaced - its storage is plugin-owned, untouched by the core reload)
        Optional<Guild> viaLiveRef = liveRef.resolve().join();
        assertTrue(viaLiveRef.isPresent(), "a live Ref from before the reload must keep resolving");
        assertEquals("Alpha", viaLiveRef.get().name);

        //a profile re-read from the backend AFTER the reload resolves too (its refs are born
        //deserialized, bound to the SAME stable registry the plugin's manager still lives in)
        PlayerController.clearPDSections(ProfileSection.class);
        assertResolvesTo(pid, gid, "Alpha");

        //and the handle never detached: it keeps handing out managers on the same registry
        assertSame(registryBefore, guildStorage.refRegistry(),
                "the plugin's registry must keep its identity across the reload");
        assertSame(guildManager(guildStorage), guildManager(guildStorage),
                "the handle keeps vending its (memoized) manager after the reload");

        guildStorage.close().join();
    }

    // ==================================================================
    //  identity: the registries are the same objects across a reload
    // ==================================================================

    @Test
    void perPluginAndGlobalRegistriesKeepTheirIdentityAcrossAReload() throws Exception {
        plugin = realPluginData();
        File storageYml = Storages.h2("registry_identity").writeTo(tempDir);
        PlayerController.initialize(storageYml);

        RefRegistry childBefore = ECStorageRegistries.of(plugin);
        assertNotNull(childBefore, "the provider answers once the controller is up");
        RefRegistry globalBefore = childBefore.parent();
        assertNotNull(globalBefore, "a plugin child is parented to the global");

        PlayerController.initialize(storageYml); //the reload

        assertSame(childBefore, ECStorageRegistries.of(plugin),
                "the per-plugin registry is the same object after the reload");
        assertSame(globalBefore, ECStorageRegistries.of(plugin).parent(),
                "the global registry is the same object after the reload");
    }

    // ==================================================================
    //  atomicity: a failed reload keeps the live instance AND its resolvers
    // ==================================================================

    @Test
    void aFailedReloadKeepsTheLiveInstanceServingItsManagers(TestPlatform platform) throws Exception {
        plugin = realPluginData();
        File storageYml = Storages.h2("atomic_live").writeTo(tempDir);
        PlayerController.initialize(storageYml);
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(plugin, ProfileSection.class, "profilesection").build());
        PlayerController live = PlayerController.get();

        //what the stable global answers for the base entity while the live instance serves
        RefRegistry global = ECStorageRegistries.of(plugin).parent();
        Object baseResolverBefore = global.resolver(PlayerData.class);
        assertNotNull(baseResolverBefore);

        //a storage.yml whose backend refuses to init (H2 file, IFEXISTS on a db that never existed):
        //the fresh constructor fails before the swap, so the live instance is untouched
        File broken = writeBrokenStorageYml();
        assertThrows(Throwable.class, () -> PlayerController.initialize(broken),
                "a reload whose fresh instance fails to construct must propagate");

        assertSame(live, PlayerController.get(), "a failed reload must NOT swap the live instance");
        assertSame(baseResolverBefore, global.resolver(PlayerData.class),
                "a failed reload must put the live instance's managers back as the registries' resolvers");
        assertTrue(platform.getShutdownReasons().isEmpty(),
                "a failed RELOAD must never stop the server - the live instance is still serving");
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
    private CachingManager<UUID, Guild> guildManager(ECStorage storage) {
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
