package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the {@code DebugMode} block of a plugin's {@code config.yml} is read, and how many times.
 * <p>
 * The enable reads it once, up front, so the file is complete before any plugin code runs; a query
 * that somehow gets there first still triggers only one read no matter how many threads race it; and
 * once it has been read, answering costs no file access at all.
 */
@ECoreTest
class DebugConfigLifecycleTest {

    @TempDir
    Path tempDir;

    private final List<String> createdPlugins = new ArrayList<>();

    @AfterEach
    void teardown() {
        for (String pluginName : createdPlugins) {
            ECPluginManager.removePluginData(pluginName);
        }
        createdPlugins.clear();
    }

    // ------------------------------------------------------------------
    //  One load, however many threads ask first
    // ------------------------------------------------------------------

    @Test
    void everyThreadRacingTheFirstQueryGetsOneSingleLoad() throws Exception {
        ECPluginData data = pluginData("DebugRace");
        data.defineDebugModules(modules(data, "ALPHA", "BETA"));

        int racers = 12;
        CyclicBarrier gate = new CyclicBarrier(racers);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<Boolean>> answers = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                answers.add(pool.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS); //nobody asks until everybody is ready to ask
                    return data.isDebugEnabled();
                }));
            }
            for (Future<Boolean> answer : answers) {
                assertFalse(answer.get(20, TimeUnit.SECONDS), "the seeded default is 'disabled'");
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, data.getDebugLoadCount(),
                racers + " threads on the first query must produce exactly one read of the file");
        assertEquals(1, data.getDebugSaveCount(),
                "and that one read is the only thing allowed to write the file back");
    }

    @Test
    void answeringAfterTheLoadNeverLooksAtTheFileAgain() {
        ECPluginData data = pluginData("DebugNoReread");
        data.defineDebugModules(modules(data, "ALPHA"));
        data.loadDebugConfig();
        assertFalse(data.isDebugEnabled());

        //an edit landing after the load is invisible until somebody loads again - which is what
        //"the query reads memory, not the disk" means in observable terms
        writeSwitch("DebugNoReread", "DebugMode.enabled", true);

        for (int i = 0; i < 1000; i++) {
            assertFalse(data.isDebugEnabled(), "the answer must still be the loaded one");
        }
        assertEquals(1, data.getDebugLoadCount(), "no query may have re-read the file");
    }

    // ------------------------------------------------------------------
    //  The enable seeds the whole block, before anything else runs
    // ------------------------------------------------------------------

    @Test
    void theEnableCompletesTheDebugBlockBeforeTheFirstHook() {
        ECPluginData data = pluginData("DebugSeed");
        data.defineDebugModules(modules(data, "ALPHA", "BETA", "GAMMA"));
        assertFalse(configFile("DebugSeed").exists(), "nothing may touch the file before the enable");

        Set<String> keysSeenByTheFirstHook = new LinkedHashSet<>();
        Boot boot = new Boot(data, () -> keysSeenByTheFirstHook.addAll(moduleKeysOnDisk("DebugSeed")));

        boot.runECPluginEnable();

        assertEquals(new LinkedHashSet<>(Arrays.asList("ALPHA", "BETA", "GAMMA")), keysSeenByTheFirstHook,
                "every module has to be listed in the file already when the first hook runs");
        assertTrue(openConfig("DebugSeed").contains("DebugMode.enabled"),
                "and so does the master switch");
        assertEquals(1, data.getDebugLoadCount(),
                "the enable is what read the file - no query had to trigger it");
    }

    // ------------------------------------------------------------------
    //  Interactions: define/load/force, in both orders
    // ------------------------------------------------------------------

    @Test
    void modulesDeclaredAfterALoadAreSeededOnTheSpot() {
        ECPluginData data = pluginData("DebugLateModules");
        data.defineDebugModules(modules(data, "ALPHA"));
        data.loadDebugConfig();
        assertEquals(new LinkedHashSet<>(Arrays.asList("ALPHA")), moduleKeysOnDisk("DebugLateModules"));

        data.defineDebugModules(modules(data, "ALPHA", "BETA"));

        assertEquals(new LinkedHashSet<>(Arrays.asList("ALPHA", "BETA")), moduleKeysOnDisk("DebugLateModules"),
                "a module declared after the block was read still has to reach the file");
        assertEquals(2, data.getDebugLoadCount(), "the re-seed is a load of its own");
    }

    @Test
    void modulesDeclaredBeforeAnyLoadTouchNoFile() {
        ECPluginData data = pluginData("DebugEarlyModules");

        data.defineDebugModules(modules(data, "ALPHA", "BETA"));

        assertEquals(0, data.getDebugLoadCount(), "declaring modules is not a reason to read anything");
        assertFalse(configFile("DebugEarlyModules").exists());
    }

    @Test
    void anExplicitLoadAlwaysRereads_andOnlySavesWhenItCompletesTheFile() {
        ECPluginData data = pluginData("DebugReload");
        data.defineDebugModules(modules(data, "ALPHA"));
        data.loadDebugConfig();
        assertFalse(data.isDebugEnabled());

        writeSwitch("DebugReload", "DebugMode.enabled", true);
        data.loadDebugConfig();

        assertTrue(data.isDebugEnabled(), "the second load has to pick up the edit");
        assertEquals(2, data.getDebugLoadCount());
        assertEquals(1, data.getDebugSaveCount(),
                "the second load found nothing missing, so it had no reason to write");
    }

    @Test
    void forcingTheSwitchBeforeAnyLoadKeepsTheFileUnread() {
        ECPluginData data = pluginData("DebugForcedFirst");
        data.defineDebugModules(modules(data, "ALPHA"));

        data.setDebugEnabled(true);

        assertTrue(data.isDebugEnabled());
        assertEquals(0, data.getDebugLoadCount(), "a forced switch counts as loaded");
        assertFalse(configFile("DebugForcedFirst").exists());

        data.loadDebugConfig();
        assertFalse(data.isDebugEnabled(), "an explicit load still overrides the forced value");
    }

    @Test
    void forcingTheSwitchAfterALoadOverridesTheFile() {
        ECPluginData data = pluginData("DebugForcedLast");
        data.defineDebugModules(modules(data, "ALPHA"));
        data.loadDebugConfig();
        assertFalse(data.isDebugEnabled());

        data.setDebugEnabled(true);

        assertTrue(data.isDebugEnabled());
        assertEquals(1, data.getDebugLoadCount(), "forcing the value is not a reload");
    }

    @Test
    void aModuleSwitchedOffInTheFileGatesItsOwnDebugOnly() {
        ECPluginData data = pluginData("DebugPerModule");
        IDebugModule[] modules = modules(data, "ALPHA", "BETA");
        data.defineDebugModules(modules);
        data.loadDebugConfig();

        writeSwitch("DebugPerModule", "DebugMode.enabled", true);
        writeSwitch("DebugPerModule", "DebugMode.DebugModules.BETA", false);
        data.loadDebugConfig();

        assertTrue(data.isDebugEnabled(), "the plugin switch is on");
        assertTrue(data.isDebugEnabled(modules[0]), "ALPHA stayed on");
        assertFalse(data.isDebugEnabled(modules[1]), "BETA was switched off in the file");
    }

    // ------------------------------------------------------------------
    //  fixture
    // ------------------------------------------------------------------

    /** A bootstrap whose only job is to look at the config file the moment the first hook runs. */
    private static final class Boot implements IECPluginBootstrap {
        private final ECPluginData data;
        private final Runnable onPre;

        Boot(ECPluginData data, Runnable onPre) {
            this.data = data;
            this.onPre = onPre;
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public void onECPluginEnablePre() {
            onPre.run();
        }

        @Override
        public void onECPluginEnable() {
        }

        @Override
        public void onECPluginReload() {
        }

        @Override
        public void onECPluginShutdown() {
        }
    }

    /** A switch that is not {@code ECDebugModule}, so these tests never move JVM-wide enum state. */
    private static final class NamedModule implements IDebugModule {
        private final String name;
        private final ECPluginData data;
        private boolean enabled;

        NamedModule(String name, ECPluginData data) {
            this.name = name;
            this.data = data;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static IDebugModule[] modules(ECPluginData data, String... names) {
        IDebugModule[] modules = new IDebugModule[names.length];
        for (int i = 0; i < names.length; i++) {
            modules[i] = new NamedModule(names[i], data);
        }
        return modules;
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        createdPlugins.add(pluginName);
        return ECPluginManager.getOrCreateECorePluginData(new Object());
    }

    private File configFile(String pluginName) {
        return new File(tempDir.resolve(pluginName).toFile(), "config.yml");
    }

    private Config openConfig(String pluginName) {
        return ConfigFactory.open(configFile(pluginName));
    }

    private Set<String> moduleKeysOnDisk(String pluginName) {
        Config config = openConfig(pluginName);
        return config.contains("DebugMode.DebugModules")
                ? new LinkedHashSet<>(config.getKeys("DebugMode.DebugModules"))
                : new LinkedHashSet<String>();
    }

    /** Writes straight into the file, the way an operator editing config.yml would. */
    private void writeSwitch(String pluginName, String path, boolean value) {
        Config config = openConfig(pluginName);
        config.setValue(path, value);
        config.save();
    }

}
