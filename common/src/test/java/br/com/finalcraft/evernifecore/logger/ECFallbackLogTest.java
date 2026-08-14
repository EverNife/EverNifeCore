package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code EverNifeCore.getLog()} on a runtime with no plugin data plugged in - a bare JUnit run, a
 * static initializer, a plugin that reached the core before it loaded. No platform, no config and no
 * test engine take part: this is the state every caller used to guard against with a try/catch of its
 * own, and the guarantee that replaced those is that the call answers with a working logger.
 */
class ECFallbackLogTest {

    private final List<LogRecord> published = new ArrayList<>();
    private final Handler collector = new Handler() {
        @Override public void publish(LogRecord record) { published.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    private ECPluginData coreDataBefore;
    private Logger julLogger;
    private Level levelBefore;

    @BeforeEach
    void unplugTheRuntime() throws ReflectiveOperationException {
        //another test class in this JVM may have left a plugin data on the core; this case is about
        //there being none, so it is set - and put back - explicitly
        coreDataBefore = EverNifeCore.getEcPluginData();
        setCoreEcPluginData(null);

        julLogger = Logger.getLogger(ECFallbackLog.LOGGER_NAME);
        levelBefore = julLogger.getLevel();
        //FINE is below the default threshold, so without this the debug case would be dropped before
        //any handler ran and the test would pass on an empty list
        julLogger.setLevel(Level.ALL);
        julLogger.addHandler(collector);
    }

    @AfterEach
    void plugItBack() throws ReflectiveOperationException {
        julLogger.removeHandler(collector);
        julLogger.setLevel(levelBefore);
        setCoreEcPluginData(coreDataBefore);
    }

    @Test
    void theCoreLoggerAnswersWithoutAPluginRuntime() {
        ECLogger log = EverNifeCore.getLog();

        assertNotNull(log, "getLog() must answer even with no plugin data plugged in");
        assertSame(ECFallbackLog.get(), log, "with no plugin data, the answer is the fallback itself");
        assertEquals(null, log.getEcPluginData(), "the fallback speaks for no plugin");
    }

    @Test
    void everyVerbReachesTheFallbackSinkAtItsOwnLevel() {
        ECLogger log = EverNifeCore.getLog();

        log.info("info line");
        log.warning("warning line");
        log.severe("severe line");
        log.debug("debug line");

        assertEquals(4, published.size(), "every verb must produce exactly one record: " + messages());
        assertEquals(Level.INFO, published.get(0).getLevel());
        assertEquals(Level.WARNING, published.get(1).getLevel());
        assertEquals(Level.SEVERE, published.get(2).getLevel());
        assertEquals(Level.FINE, published.get(3).getLevel(),
                "debug maps to FINE, which is what kept it quiet before any of this existed");
        assertEquals("[Debug] debug line", published.get(3).getMessage());
    }

    @Test
    void theFallbackFormatsWithTheSamePlaceholdersAsAnyOtherLogger() {
        EverNifeCore.getLog().warning("backend '{}' unreachable, using {}", "mysql", "local");

        assertEquals(1, published.size());
        assertEquals("backend 'mysql' unreachable, using local", published.get(0).getMessage());
    }

    @Test
    void aTrailingThrowableStillGetsItsStackTraceAppended() {
        EverNifeCore.getLog().severe("could not open {}", "trades.log", new IllegalStateException("disk on fire"));

        assertEquals(1, published.size());
        String message = published.get(0).getMessage();
        assertTrue(message.startsWith("could not open trades.log"), message);
        assertTrue(message.contains("IllegalStateException: disk on fire"), message);
        //a frame, not the exception's name: rendered as a surplus argument it would reach the line
        //too, so the two assertions above hold just as well with no stack trace at all
        assertTrue(message.contains("\tat " + ECFallbackLogTest.class.getName()),
                "the trailing throwable's stack trace belongs in the line: " + message);
    }

    /** A hostile argument is the classic way a log line takes the server down with it. */
    @Test
    void aParameterThatBlowsUpDoesNotEscapeTheCall() {
        Object hostile = new Object() {
            @Override public String toString() { throw new IllegalStateException("no"); }
        };

        EverNifeCore.getLog().info("value: {}", hostile);

        assertEquals(1, published.size());
        assertTrue(published.get(0).getMessage().contains("toString failed"), messages().toString());
    }

    private List<String> messages() {
        List<String> lines = new ArrayList<>();
        for (LogRecord record : published) {
            lines.add(record.getLevel() + " " + record.getMessage());
        }
        return lines;
    }

    private static void setCoreEcPluginData(ECPluginData ecPluginData) throws ReflectiveOperationException {
        Field field = EverNifeCore.class.getDeclaredField("ecPluginData");
        field.setAccessible(true);
        field.set(EverNifeCore.instance, ecPluginData);
    }
}
