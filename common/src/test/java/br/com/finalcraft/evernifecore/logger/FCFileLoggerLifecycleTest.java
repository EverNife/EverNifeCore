package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file logger opened from a plugin belongs to that plugin: its shutdown closes what was left open,
 * and it does so after the last teardown hook, so the plugin's closing lines are still written.
 */
@ECoreTest
class FCFileLoggerLifecycleTest {

    @TempDir
    Path tempDir;

    private final List<String> createdPlugins = new ArrayList<>();

    @AfterEach
    void teardown() {
        for (String name : createdPlugins) {
            ECPluginManager.removePluginData(name);
        }
        createdPlugins.clear();
    }

    @Test
    void theShutdownClosesTheHandleThePluginLeftOpen() {
        TestBootstrap plugin = bootstrap("FileLoggerShutdown");
        FCFileLogger trades = FCFileLogger.of(plugin.getPluginData(), "trades.log").build();

        assertEquals(Arrays.asList(trades), plugin.getPluginData().getOpenFileLoggers(),
                "the owning port registers the handle, which is what makes the sweep possible");
        assertEquals("logs", trades.getFile().getParentFile().getName(),
                "the owning port writes under <dataFolder>/logs/");

        plugin.runECPluginShutdown();

        assertFalse(trades.isOpen(), "a plugin that forgot to close still does not leak the handle");
        assertTrue(plugin.getPluginData().getOpenFileLoggers().isEmpty(),
                "and the bookkeeping does not outlive the plugin either");
    }

    @Test
    void thePluginStillWritesItsLastLinesDuringItsOwnTeardown() throws IOException {
        TestBootstrap plugin = bootstrap("FileLoggerLastLines");
        FCFileLogger trades = FCFileLogger.of(plugin.getPluginData(), "trades.log").build();
        plugin.duringShutdown = () -> trades.log("closing the books");

        plugin.runECPluginShutdown();

        assertEquals(Arrays.asList("closing the books"),
                Files.readAllLines(trades.getFile().toPath(), StandardCharsets.UTF_8),
                "the sweep runs AFTER the last hook - a sweep before it would swallow this line");
    }

    @Test
    void aHandleThePluginClosedItselfIsNoProblemForTheSweep() throws IOException {
        TestBootstrap plugin = bootstrap("FileLoggerSelfClosed");
        FCFileLogger trades = FCFileLogger.of(plugin.getPluginData(), "trades.log").build();
        plugin.duringShutdown = () -> {
            trades.log("done");
            trades.close();
        };

        plugin.runECPluginShutdown();

        assertFalse(trades.isOpen());
        assertTrue(plugin.getPluginData().getOpenFileLoggers().isEmpty(),
                "closing it yourself deregisters it, so the sweep never even sees it");
        assertEquals(Arrays.asList("done"),
                Files.readAllLines(trades.getFile().toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void aHandleThatRefusesToCloseDoesNotKeepTheOthersOpen() {
        TestBootstrap plugin = bootstrap("FileLoggerRefusing");
        ECPluginData pluginData = plugin.getPluginData();
        FCFileLogger wellBehaved = FCFileLogger.of(pluginData, "trades.log").build();
        RefusingToClose refusing = new RefusingToClose(tempDir.resolve("refusing.log").toFile());
        pluginData.trackOpenFileLogger(refusing);

        plugin.runECPluginShutdown();

        assertEquals(1, refusing.closeAttempts.get(), "the sweep did reach the one that throws");
        assertFalse(wellBehaved.isOpen(), "and the failure did not cost the handle next in line");
        assertTrue(pluginData.getOpenFileLoggers().isEmpty());
    }

    @Test
    void writingToAHandleTheShutdownClosedSaysNothingAtAll(TestPlatform platform) {
        TestBootstrap plugin = bootstrap("FileLoggerAfterShutdown");
        FCFileLogger trades = FCFileLogger.of(plugin.getPluginData(), "trades.log").build();
        plugin.runECPluginShutdown();

        int loggedBefore = platform.getLoggedMessages().size();
        trades.log("nobody is listening");
        trades.log("nor to this one");

        assertEquals(loggedBefore, platform.getLoggedMessages().size(),
                "a closed handle drops the line and stays quiet - a task still writing to it after the"
                        + " shutdown would otherwise turn one forgotten close into a console flood");
    }

    @Test
    void aHandleWithNoOwnerIsNotTheShutdownsBusiness() throws IOException {
        TestBootstrap plugin = bootstrap("FileLoggerOwnerless");
        FCFileLogger ownerless = FCFileLogger.of(tempDir.resolve("free.log").toFile()).build();

        assertTrue(plugin.getPluginData().getOpenFileLoggers().isEmpty(),
                "the ownerless ports register nothing - whoever opened one closes it");

        plugin.runECPluginShutdown();

        assertTrue(ownerless.isOpen(), "a handle nobody handed over is not the shutdown's to close");
        ownerless.log("still mine to write");
        ownerless.close();
        assertEquals(Arrays.asList("still mine to write"),
                Files.readAllLines(ownerless.getFile().toPath(), StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    //  fixture
    // ------------------------------------------------------------------

    private TestBootstrap bootstrap(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        createdPlugins.add(pluginName);
        TestBootstrap plugin = new TestBootstrap();
        //the default onECPluginShutdownPre() touches FinalCMDManager, whose static block logs through
        //EverNifeCore.getEcPluginData() - it must be non-null the FIRST time any test in the JVM
        //references that class, or its <clinit> fails permanently for the run
        EverNifeCore.instance.onLoaderInstantiate(plugin.getPluginData());
        return plugin;
    }

    private static final class TestBootstrap implements IECPluginBootstrap {

        private Runnable duringShutdown;

        @Override public void onECPluginEnable() { }

        @Override public void onECPluginShutdown() {
            if (duringShutdown != null) {
                duringShutdown.run();
            }
        }

        @Override public void onECPluginReload() { }
    }

    /** A handle whose close() throws - what a subclass, or a stream broken past repair, produces. */
    private static final class RefusingToClose extends FCFileLogger {

        private final AtomicInteger closeAttempts = new AtomicInteger();

        RefusingToClose(File file) {
            super(FCFileLogger.of(file));
        }

        @Override
        public void close() {
            closeAttempts.incrementAndGet();
            throw new IllegalStateException("this handle refuses to close");
        }
    }
}
