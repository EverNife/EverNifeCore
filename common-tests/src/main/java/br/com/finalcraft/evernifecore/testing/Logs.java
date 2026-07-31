package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures what EverNifeCore logged while a block of code ran.
 *
 * <p>Some behaviour has no return value and no state to inspect - a bind that WARNS about a risky
 * backend still binds, and the warning is the entire observable effect. Without this, a test can only
 * assert that nothing threw, which is also what a silently broken guard does.</p>
 *
 * <pre>{@code
 * List<String> logged = Logs.capture(() -> PlayerController.initialize(yml));
 * assertTrue(logged.stream().anyMatch(line -> line.contains("optimistic lock")));
 *
 * // for behaviour that warns AND then throws:
 * List<String> logged = Logs.captureThrowing(() -> PlayerController.initialize(badYml));
 * }</pre>
 *
 * <p>Match on the message TEXT, never on a level or an index. It reads three sinks whose liveness
 * depends on whether a plugin runtime is installed, so the result is concatenated blocks rather than
 * a chronological stream and a line can appear twice. {@code anyMatch} is the assertion this class
 * supports; {@code count()} reads a sequence nobody promised.
 *
 * <p>It replaces the process-global {@code System.out} for the duration, so an assertion must be
 * specific enough that a parallel test's boot could not satisfy it. {@code System.err} is not
 * captured at all.
 */
public final class Logs {

    private static final String LOGGER_NAME = "EverNifeCore";

    private Logs() {
    }

    /** Every line logged while {@code action} ran. Rethrows whatever {@code action} threw. */
    public static List<String> capture(Runnable action) {
        List<String> captured = new ArrayList<String>();
        RuntimeException failure = null;
        try {
            captureInto(action, captured);
        } catch (RuntimeException thrown) {
            failure = thrown;
        }
        if (failure != null) {
            throw failure;
        }
        return captured;
    }

    /**
     * As {@link #capture}, but SWALLOWS what {@code action} threw and still returns what it logged -
     * for behaviour that warns and then refuses, where both halves are the point.
     */
    public static List<String> captureThrowing(Runnable action) {
        List<String> captured = new ArrayList<String>();
        try {
            captureInto(action, captured);
        } catch (RuntimeException ignoredOnPurpose) {
            //the caller asked for the log, not the outcome; the lines captured so far still stand
        }
        return captured;
    }

    /** Drains all three sinks into {@code captured} even when {@code action} blows up half way. */
    private static void captureInto(Runnable action, List<String> captured) {
        //thread-safe: the write-back flusher, the idle sweep and cache-sync all log from their own
        //threads while the action runs, and a plain ArrayList would drop or corrupt those lines
        List<String> viaLogger = new CopyOnWriteArrayList<String>();
        Logger logger = Logger.getLogger(LOGGER_NAME);
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                viaLogger.add(String.valueOf(record.getMessage()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        //a logger left at a coarser level would drop the very records under test before any handler ran
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.addHandler(collector);

        TestPlatform platform = installedTestPlatform();
        int alreadyLogged = platform != null ? platform.getLoggedMessages().size() : 0;

        ByteArrayOutputStream stdoutCopy = new ByteArrayOutputStream();
        PrintStream previousOut = System.out;
        System.setOut(new PrintStream(new TeeOutputStream(previousOut, stdoutCopy), true));
        try {
            action.run();
        } finally {
            //drained in the finally: a helper whose whole purpose is asserting on log-only behaviour
            //is useless for the warns-then-throws case if the throw discards what it captured
            System.out.flush();
            System.setOut(previousOut);
            logger.removeHandler(collector);
            logger.setLevel(previousLevel);

            Set<String> seen = new LinkedHashSet<String>();
            seen.addAll(viaLogger);
            if (platform != null) {
                List<String> viaPlatform = new ArrayList<String>(platform.getLoggedMessages());
                if (alreadyLogged < viaPlatform.size()) {
                    seen.addAll(viaPlatform.subList(alreadyLogged, viaPlatform.size()));
                }
            }
            for (String line : stdoutCopy.toString().split("\\R")) {
                if (!line.isEmpty()) {
                    seen.add(line);
                }
            }
            captured.addAll(seen);
        }
    }

    /**
     * The platform double currently registered, or {@code null} when the core has no platform yet.
     * Not enough on its own: an {@code ECLogger} keeps the adapter it was built with, so a plugin that
     * outlives one test keeps logging into the platform double that created it, not into today's.
     * That is why stdout - which every adapter writes to - is read as well.
     */
    private static TestPlatform installedTestPlatform() {
        try {
            IPlatform platform = EverNifeCore.getPlatform();
            return platform instanceof TestPlatform ? (TestPlatform) platform : null;
        } catch (Throwable noPlatformRegistered) {
            return null;
        }
    }

    /** Writes through to the real stream and keeps a copy, so captured output still reaches the console. */
    private static final class TeeOutputStream extends OutputStream {

        private final OutputStream through;
        private final OutputStream copy;

        TeeOutputStream(OutputStream through, OutputStream copy) {
            this.through = through;
            this.copy = copy;
        }

        @Override
        public void write(int b) throws IOException {
            //the copy goes FIRST: a console stream already torn down by the harness would otherwise
            //throw and take the capture with it, leaving a test green against an empty list
            copy.write(b);
            through.write(b);
        }

        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            copy.write(bytes, off, len);
            through.write(bytes, off, len);
        }

        @Override
        public void flush() throws IOException {
            copy.flush();
            through.flush();
        }
    }
}
