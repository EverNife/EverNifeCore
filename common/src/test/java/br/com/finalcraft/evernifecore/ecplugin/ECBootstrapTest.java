package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Who an {@link ECBootstrap} hands out along the lifecycle: the instance from the moment the platform
 * builds it, nothing once it has shut down, and the instance again if it is enabled a second time -
 * all of it published by the orchestration, with the plugin writing no bookkeeping of its own.
 */
@ECoreTest
class ECBootstrapTest {

    @TempDir
    Path tempDir;

    private final List<String> registeredNames = new ArrayList<>();

    @AfterEach
    void teardown() {
        Bootstraps.forgetAll(); //a test double must not outlive its test inside a holder
        for (String name : registeredNames) {
            ECPluginManager.removePluginData(name);
        }
        registeredNames.clear();
    }

    @Test
    void theInstanceIsPublishedTheMomentItIsBuilt() {
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);

        TestPlugin plugin = new TestPlugin(pluginData("PublishOnBuild"));

        assertSame(plugin, holder.get(),
                "nothing but the constructor ran, and the plugin is already reachable");
    }

    @Test
    void aHolderBuiltAfterThePublishIsSeededWithIt() {
        TestPlugin plugin = new TestPlugin(pluginData("LateHolder"));

        //an interface is only initialized when something first reads it, so its holder can be born
        //after the plugin it is for
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);

        assertSame(plugin, holder.get());
    }

    @Test
    void theEnabledInstanceIsTheOnePublished() {
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);
        TestPlugin plugin = new TestPlugin(pluginData("PublishOnEnable"));

        plugin.runECPluginEnable();

        assertSame(plugin, holder.get());
    }

    @Test
    void theShutDownPluginIsNotHandedOut() {
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);
        TestPlugin plugin = new TestPlugin(pluginData("ClearOnShutdown"));
        plugin.runECPluginEnable();

        plugin.runECPluginShutdown();

        assertNull(holder.get(), "a plugin that already tore itself down is worse than no plugin at all");
    }

    @Test
    void theTeardownItselfCanStillReachThePlugin() {
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);
        TestPlugin plugin = new TestPlugin(pluginData("ReachableWhileClosing"));
        plugin.holder = holder;
        plugin.runECPluginEnable();

        plugin.runECPluginShutdown();

        assertSame(plugin, plugin.seenByItsOwnShutdown,
                "the instance is taken back only after the last shutdown hook has run");
    }

    @Test
    void enablingTheSameObjectAgainPublishesItBack() {
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);
        TestPlugin plugin = new TestPlugin(pluginData("ReEnable"));
        plugin.runECPluginEnable();
        plugin.runECPluginShutdown();

        plugin.runECPluginEnable(); //a plugin manager disables and re-enables the object it already has

        assertSame(plugin, holder.get(),
                "the constructor does not run a second time, so only the enable can publish it back");
    }

    @Test
    void aLateShutdownDoesNotUnpublishTheInstanceThatReplacedIt() {
        ECBootstrap<TestBootstrap> holder = ECBootstrap.of(TestBootstrap.class);
        ECPluginData data = pluginData("LateShutdown");
        TestPlugin replaced = new TestPlugin(data);
        TestPlugin current = new TestPlugin(data);

        replaced.runECPluginShutdown();

        assertSame(current, holder.get(), "the outgoing instance may only take back its own publication");
    }

    @Test
    void aHolderIgnoresAPluginOfAnotherType() {
        ECBootstrap<OtherBootstrap> holder = ECBootstrap.of(OtherBootstrap.class);

        new TestPlugin(pluginData("ForeignPlugin"));

        assertNull(holder.get(), "one plugin's holder must not be filled by another plugin");
    }

    // ------------------------------------------------------------------
    // fakes: a plugin's common bootstrap and the platform object implementing it
    // ------------------------------------------------------------------

    interface TestBootstrap extends IECPluginBootstrap {
        @Override
        default void onECPluginShutdown() {
        }

        @Override
        default void onECPluginReload() {
        }
    }

    /** A second plugin's bootstrap, unrelated to {@link TestBootstrap}. */
    interface OtherBootstrap extends IECPluginBootstrap {
    }

    /** Stands in for ECBukkitPlugin / ECHytalePlugin, which both run the instantiate entry point from their constructor. */
    static final class TestPlugin implements TestBootstrap {

        private final ECPluginData data;

        /** Set by the test that wants to know what this plugin's own teardown can still read. */
        ECBootstrap<TestBootstrap> holder;
        IECPluginBootstrap seenByItsOwnShutdown;

        TestPlugin(ECPluginData data) {
            this.data = data;
            runECPluginInstantiate();
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public void onECPluginEnable() {
        }

        @Override
        public void onECPluginShutdownPost() {
            //the last hook of the teardown: whoever is published here is what a plugin closing its own
            //resources reads through its accessor
            if (holder != null) {
                seenByItsOwnShutdown = holder.get();
            }
        }
    }

    // ------------------------------------------------------------------
    // fixture: a real ECPluginData built through the extractor, as production does
    // ------------------------------------------------------------------

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredNames.add(pluginName);
        ECPluginData data = ECPluginManager.getOrCreateECorePluginData(new Object());
        //the default pre-shutdown reaches FinalCMDManager, whose static block logs through
        //EverNifeCore.getEcPluginData(): non-null the first time, or its <clinit> fails for the whole run
        EverNifeCore.instance.onLoaderInstantiate(data);
        return data;
    }
}
