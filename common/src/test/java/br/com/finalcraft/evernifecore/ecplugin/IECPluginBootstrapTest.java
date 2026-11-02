package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.flag.ActorHolders;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestActorHolder;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bootstrap orchestration: enable runs Pre -&gt; main -&gt; Post then schedules the first-tick
 * task; shutdown runs Pre -&gt; main -&gt; Post; and the default pre-shutdown unregisters every
 * listener the plugin registered BEFORE the main teardown runs.
 */
@ECoreTest
class IECPluginBootstrapTest {


    @TempDir
    Path tempDir;

    private final List<String> registeredNames = new ArrayList<>();

    @AfterEach
    void teardown() {
        for (String name : registeredNames) {
            ECPluginManager.removePluginData(name);
        }
        registeredNames.clear();
    }

    @Test
    void enableRunsPreMainPostInOrder() {
        RecordingBootstrap boot = new RecordingBootstrap(pluginData("BootEnable"));

        boot.runECPluginEnable();

        assertEquals(Arrays.asList("enablePre", "enable", "enablePost"), boot.order);
    }

    @Test
    void enableSchedulesTheFirstTickTaskWhenPresent() {
        RecordingBootstrap boot = new RecordingBootstrap(pluginData("BootFirstTick"));
        AtomicBoolean firstTickRan = new AtomicBoolean(false);
        boot.firstTick = () -> firstTickRan.set(true);

        boot.runECPluginEnable();

        //the test platform runs the first-tick task in place, so it has already run
        assertTrue(firstTickRan.get(), "a non-null runOnFirstTick() must be scheduled");
    }

    @Test
    void theFirstTickTaskIsHandedToTheMainThreadAndNeverRunsInsideEnable() {
        RecordingBootstrap boot = new RecordingBootstrap(pluginData("BootFirstTickLater"));
        AtomicBoolean firstTickRan = new AtomicBoolean(false);
        boot.firstTick = () -> firstTickRan.set(true);
        List<Supplier<?>> deliveredLater = new ArrayList<>();
        ECScheduler.MainThread previous = ECScheduler.getMainThread();
        ECScheduler.setMainThread(new ECScheduler.MainThread() {
            @Override
            public <T> CompletableFuture<T> onMainThread(Supplier<T> task) {
                throw new AssertionError("the first tick task must never be offered to run in place");
            }

            @Override
            public <T> CompletableFuture<T> onMainThreadLater(Supplier<T> task) {
                deliveredLater.add(task);
                return new CompletableFuture<>();
            }
        });
        try {
            boot.runECPluginEnable();
        } finally {
            ECScheduler.setMainThread(previous);
        }

        assertFalse(firstTickRan.get(), "enable returned before the first tick task ran");
        assertEquals(1, deliveredLater.size(), "exactly one hand-off to onMainThreadLater");

        deliveredLater.get(0).get();

        assertTrue(firstTickRan.get(), "running what was handed over runs the first tick task");
    }

    @Test
    void enableWithNullFirstTickRunsNothingAndDoesNotThrow() {
        RecordingBootstrap boot = new RecordingBootstrap(pluginData("BootNoFirstTick"));
        boot.firstTick = null;

        boot.runECPluginEnable();

        assertEquals(Arrays.asList("enablePre", "enable", "enablePost"), boot.order);
    }

    @Test
    void shutdownRunsPreMainPostInOrder() {
        RecordingBootstrap boot = new RecordingBootstrap(pluginData("BootShutdown"));

        boot.runECPluginShutdown();

        assertEquals(Arrays.asList("shutdownPre", "shutdown", "shutdownPost"), boot.order);
    }

    @Test
    void aThrowingMainTeardownStillRunsPostAndRethrows() {
        RecordingBootstrap boot = new RecordingBootstrap(pluginData("BootShutdownThrows"));
        boot.failInShutdown = true;

        IllegalStateException thrown = assertThrows(IllegalStateException.class, boot::runECPluginShutdown);

        assertEquals("teardown failed", thrown.getMessage());
        assertEquals(Arrays.asList("shutdownPre", "shutdown", "shutdownPost"), boot.order);
    }

    @Test
    void defaultShutdownPreUnregistersListenersBeforeTheMainTeardown() {
        ECPluginData data = pluginData("BootUnregister");
        RecordingBootstrap boot = new RecordingBootstrap(data);
        boot.toRegister.add(new FakeListener());
        boot.toRegister.add(new FakeListener());

        boot.runECPluginEnable();
        assertEquals(2, ECListener.getRegistered(data).size(), "both listeners must be tracked after enable");

        boot.runECPluginShutdown();
        assertEquals(0, boot.registeredCountAtMainShutdown,
                "the pre-shutdown must unregister the listeners BEFORE onECPluginShutdown() runs");
        assertTrue(ECListener.getRegistered(data).isEmpty(), "no listener may remain after shutdown");
    }

    /** A type only this test parses, so no other registration can answer for it. */
    public static final class ShutdownToken {
    }

    public static final class ShutdownTokenParser extends ArgParser<ShutdownToken> {
        public ShutdownTokenParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<ShutdownToken> parse(@Nonnull ParseCall call) {
            return ParseResult.empty();
        }
    }

    @Test
    void defaultShutdownPreTakesBackTheGlobalParsersThePluginOwns() {
        ECPluginData data = pluginData("BootParsers");
        RecordingBootstrap boot = new RecordingBootstrap(data);
        boot.runECPluginEnable();
        ArgParserManager.addGlobalParser(data, ShutdownToken.class, ShutdownTokenParser.class);
        assertSame(ShutdownTokenParser.class, ArgParserManager.getParser(data, ShutdownToken.class));

        boot.runECPluginShutdown();

        assertNull(ArgParserManager.getParser(data, ShutdownToken.class), "the global parser left with its owner");
    }

    @Test
    void defaultShutdownPreDropsTheActorHoldersThePluginRegistered() {
        ECPluginData data = pluginData("BootActorHolders");
        RecordingBootstrap boot = new RecordingBootstrap(data);
        boot.runECPluginEnable();
        TestActorHolder party = new TestActorHolder("boot-party");
        ActorHolders.register(data, TestActorHolder.providerOf(Collections.singletonList(party)));
        assertTrue(ActorHolders.all().contains(party));

        boot.runECPluginShutdown();

        assertFalse(ActorHolders.all().contains(party), "the provider left with its owner");
    }

    @Test
    void removePluginDataDropsTheActorHoldersThePluginRegistered() {
        ECPluginData data = pluginData("RemovedActorHolders");
        TestActorHolder party = new TestActorHolder("removed-party");
        ActorHolders.register(data, TestActorHolder.providerOf(Collections.singletonList(party)));
        assertTrue(ActorHolders.all().contains(party));

        ECPluginManager.removePluginData("RemovedActorHolders");

        assertFalse(ActorHolders.all().contains(party), "the provider left with the plugin's data");
    }

    // ------------------------------------------------------------------
    // fakes
    // ------------------------------------------------------------------

    /** Records the hook order; keeps the default pre-shutdown cleanup by delegating to super. */
    static final class RecordingBootstrap implements IECPluginBootstrap {
        final ECPluginData data;
        final List<String> order = new ArrayList<>();
        final List<ECListener> toRegister = new ArrayList<>();
        Runnable firstTick;
        int registeredCountAtMainShutdown = -1;
        boolean failInShutdown;

        RecordingBootstrap(ECPluginData data) {
            this.data = data;
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public void onECPluginEnablePre() {
            order.add("enablePre");
        }

        @Override
        public void onECPluginEnable() {
            order.add("enable");
            for (ECListener listener : toRegister) {
                ECListener.register(data, listener);
            }
        }

        @Override
        public void onECPluginEnablePost() {
            order.add("enablePost");
        }

        @Override
        public void onECPluginShutdownPre() {
            order.add("shutdownPre");
            IECPluginBootstrap.super.onECPluginShutdownPre(); //keep the default listener cleanup
        }

        @Override
        public void onECPluginShutdown() {
            order.add("shutdown");
            //snapshot taken in the main teardown: proves the pre-shutdown already unregistered them
            registeredCountAtMainShutdown = ECListener.getRegistered(data).size();
            if (failInShutdown) throw new IllegalStateException("teardown failed");
        }

        @Override
        public void onECPluginShutdownPost() {
            order.add("shutdownPost");
        }

        @Override
        public void onECPluginReload() {
            order.add("reload");
        }

        @Override
        public Runnable runOnFirstTick() {
            return firstTick;
        }
    }

    static final class FakeListener implements ECListener {
        @Override
        public boolean silentRegistration() {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // fixture: a real ECPluginData built through the extractor (as production does)
    // ------------------------------------------------------------------

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredNames.add(pluginName);
        ECPluginData data = ECPluginManager.getOrCreateECorePluginData(new Object());
        //The default onECPluginShutdownPre() now also touches FinalCMDManager (unregisterAllCommands),
        //whose static block logs through EverNifeCore.getEcPluginData() - it must be non-null the FIRST
        //time any test in the JVM references that class, or its <clinit> fails permanently for the run.
        EverNifeCore.instance.onLoaderInstantiate(data);
        return data;
    }


}
