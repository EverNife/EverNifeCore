package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.PlatformId;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A debug module declares which platforms it exists on, and the platform it does not run on never
 * learns about it: no key seeded, a key inherited from elsewhere taken back, and no line logged even
 * when the file says the switch is on.
 */
class ECDebugModulePlatformGateTest {

    /** The modules every platform has - the ones no {@code ECDebugModule} restricts. */
    private static final List<String> UNIVERSAL_MODULES = Arrays.asList(
            "ARG_PARSER", "CONTEXTUAL_ARG_PARSER", "SV_WORLD_DATA", "COMMAND_REGISTRY");

    private static final String HYTALE_ONLY_MODULE = "HYTALE_FPLAYER";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private final List<String> createdPlugins = new ArrayList<>();
    private final boolean[] moduleStateBefore = new boolean[ECDebugModule.values().length];

    @BeforeEach
    void rememberModuleState() {
        for (ECDebugModule module : ECDebugModule.values()) {
            moduleStateBefore[module.ordinal()] = module.isEnabled();
        }
    }

    @AfterEach
    void teardown() {
        //ECDebugModule keeps its switch on the constant, so it is JVM-wide state this test borrowed
        for (ECDebugModule module : ECDebugModule.values()) {
            module.setEnabled(moduleStateBefore[module.ordinal()]);
        }
        for (String pluginName : createdPlugins) {
            ECPluginManager.removePluginData(pluginName);
        }
        createdPlugins.clear();
        if (world != null) {
            world.close();
            world = null;
        }
    }

    // ------------------------------------------------------------------
    //  What each platform's config.yml ends up listing
    // ------------------------------------------------------------------

    @Test
    void aMinecraftServerIsNeverOfferedTheHytaleModule() {
        ECPluginData data = coreOn(PlatformId.MINECRAFT, "GateMinecraft");

        data.loadDebugConfig();

        Set<String> seeded = moduleKeysOnDisk("GateMinecraft");
        assertTrue(seeded.containsAll(UNIVERSAL_MODULES), "every universal module has to be there: " + seeded);
        assertFalse(seeded.contains(HYTALE_ONLY_MODULE),
                "a module of another platform must not be offered as a switch: " + seeded);
    }

    @Test
    void aHytaleServerIsOfferedTheHytaleModule() {
        ECPluginData data = coreOn(PlatformId.HYTALE, "GateHytale");

        data.loadDebugConfig();

        Set<String> seeded = moduleKeysOnDisk("GateHytale");
        assertTrue(seeded.contains(HYTALE_ONLY_MODULE), "this is the platform that has it: " + seeded);
        assertTrue(seeded.containsAll(UNIVERSAL_MODULES), seeded.toString());
    }

    @Test
    void aKeyInheritedFromAnotherPlatformIsTakenBackByTheVeryFirstLoad() {
        ECPluginData data = coreOn(PlatformId.MINECRAFT, "GateSelfHeal");
        //a config.yml carried over from a Hytale server, or written by a build before the gate existed
        writeConfig(data, "DebugMode.DebugModules." + HYTALE_ONLY_MODULE, true);
        assertTrue(moduleKeysOnDisk("GateSelfHeal").contains(HYTALE_ONLY_MODULE), "fixture check");

        data.loadDebugConfig();

        assertFalse(moduleKeysOnDisk("GateSelfHeal").contains(HYTALE_ONLY_MODULE),
                "the orphan key has to be gone from the FILE, not merely ignored in memory");
    }

    @Test
    void takingBackAnInheritedKeyIsByItselfReasonToRewriteTheFile() {
        ECPluginData data = coreOn(PlatformId.MINECRAFT, "GateSelfHealAlone");
        data.loadDebugConfig(); //the file is now complete for this platform

        //with nothing left to seed, persisting the removal is the next load's ONLY reason to write -
        //which is what tells self-healing apart from riding on some other key's seeding
        writeConfig(data, "DebugMode.DebugModules." + HYTALE_ONLY_MODULE, true);

        data.loadDebugConfig();

        assertFalse(moduleKeysOnDisk("GateSelfHealAlone").contains(HYTALE_ONLY_MODULE),
                "the removal has to reach the file on its own");
    }

    // ------------------------------------------------------------------
    //  And it cannot be talked back on
    // ------------------------------------------------------------------

    @Test
    void aModuleOfAnotherPlatformStaysSilentEvenWithDebugFullyOn() {
        ECPluginData data = coreOn(PlatformId.MINECRAFT, "GateSilent");
        writeConfig(data,
                "DebugMode.enabled", true,
                "DebugMode.DebugModules." + HYTALE_ONLY_MODULE, true);

        data.loadDebugConfig();

        assertTrue(data.isDebugEnabled(), "fixture check: the master switch is on");

        List<String> logged = world.platform().getLoggedMessages();
        int before = logged.size();
        ECDebugModule.HYTALE_FPLAYER.debug("this line must not exist");
        assertEquals(before, logged.size(),
                "a module this platform does not have may not log, whatever the file says: " + logged);

        ECDebugModule.ARG_PARSER.debug("this one must");
        assertTrue(logged.size() > before, "a module this platform does have still logs: " + logged);
    }

    // ------------------------------------------------------------------
    //  fixture
    // ------------------------------------------------------------------

    /**
     * A core plugin data on {@code platformId}, wired in as THE core's own so the
     * {@code ECDebugModule} constants log through it.
     */
    private ECPluginData coreOn(String platformId, String pluginName) {
        world = Platforms.lenient().platformProviderId(platformId).install()
                .withPluginExtractor(Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        createdPlugins.add(pluginName);
        ECPluginData data = ECPluginManager.getOrCreateECorePluginData(new Object());
        EverNifeCore.instance.onLoaderInstantiate(data); //also declares ECDebugModule.values()
        return data;
    }

    /** Writes path/value pairs into the plugin's real config.yml, the way an operator would. */
    private void writeConfig(ECPluginData data, Object... pathsAndValues) {
        Config config = ConfigFactory.open(data, "config.yml");
        for (int i = 0; i < pathsAndValues.length; i += 2) {
            config.setValue((String) pathsAndValues[i], pathsAndValues[i + 1]);
        }
        config.save();
    }

    private Set<String> moduleKeysOnDisk(String pluginName) {
        File file = new File(tempDir.resolve(pluginName).toFile(), "config.yml");
        Config config = ConfigFactory.open(file);
        return config.contains("DebugMode.DebugModules")
                ? new LinkedHashSet<>(config.getKeys("DebugMode.DebugModules"))
                : new LinkedHashSet<String>();
    }

}
