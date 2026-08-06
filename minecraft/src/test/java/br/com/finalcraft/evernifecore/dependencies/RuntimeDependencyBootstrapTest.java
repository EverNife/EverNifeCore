package br.com.finalcraft.evernifecore.dependencies;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How EverNifeCore gets its runtime libraries, and the two things about that which are not free to
 * change.
 *
 * <p>The <b>when</b> is class initialization, and it has to stay there. A plugin that depends on
 * EverNifeCore can touch one of its classes before EverNifeCore is enabled - Bukkit constructs every
 * plugin before enabling any - so the class initializer is the only hook that still runs in time.
 * Moving the download into an enable hook would leave that plugin running against libraries nobody
 * downloaded yet.</p>
 *
 * <p>The <b>what happens when it fails</b> is a report, never an exception. Anything thrown out of a
 * class initializer poisons the class permanently: every later touch answers {@code
 * NoClassDefFoundError} without naming the original cause, and the first thing that touches it is the
 * scheduler every other part of the plugin goes through.</p>
 */
class RuntimeDependencyBootstrapTest {

    /** Unique per test, because a JDK logger is a singleton for its name. */
    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    @TempDir
    Path tempDir;

    private final List<CapturedLog> captured = new ArrayList<>();

    @AfterEach
    void detachLogHandlers() {
        for (CapturedLog log : captured) {
            log.detach();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A class loader that cannot be injected into
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aClassLoaderThatCannotBeInjectedIntoIsReportedRatherThanThrownAt() {
        CapturedLog log = captureLog();
        //since Java 9 the application class loader is not a URLClassLoader either; this stands in for
        //any runtime whose loader is not one
        DependencyManager manager = new DependencyManager(log.pluginName, tempDir.toFile(), "libs",
                new ClassLoader(null) {
                });

        assertFalse(manager.canInjectLibraries());
        assertEquals(1, log.getMessages().size(), "the refusal is reported once, at construction: "
                + log.getMessages());
        String reported = log.getMessages().get(0);
        assertTrue(reported.contains("URLClassLoader"), reported);
        assertTrue(reported.contains("server's classpath"), "the message has to say what to do about "
                + "it, not only what broke: " + reported);
    }

    @Test
    void aLibraryThatCannotBeInjectedIsNamedInsteadOfSilentlyDropped() {
        CapturedLog log = captureLog();
        DependencyManager manager = new DependencyManager(log.pluginName, tempDir.toFile(), "libs",
                new ClassLoader(null) {
                });

        manager.addToClasspath(tempDir.resolve("jackson-databind.jar"));

        assertEquals(2, log.getMessages().size(), log.getMessages().toString());
        assertTrue(log.getMessages().get(1).contains("jackson-databind.jar"), log.getMessages().get(1));
    }

    @Test
    void theOrdinaryCaseIsStillTheOneThatInjects() {
        CapturedLog log = captureLog();
        DependencyManager manager = new DependencyManager(log.pluginName, tempDir.toFile(), "libs",
                new java.net.URLClassLoader(new java.net.URL[0], null));

        assertTrue(manager.canInjectLibraries());
        assertTrue(log.getMessages().isEmpty(), "nothing to report: " + log.getMessages());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Where the download is triggered from
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theDownloadHangsOffClassInitialisationAndOffNothingElse() throws IOException {
        String source = bukkitEntryPointSource();

        assertEquals(1, occurrencesOf(source, "ECoreDependencies.initialize()"),
                "the download is asked for in exactly one place");
        assertTrue(classInitialiserOf(source).contains("ECoreDependencies.initialize()"),
                "and that place is the class initializer - the only hook that still runs for a plugin "
                        + "that reaches an EverNifeCore class before EverNifeCore is enabled");
    }

    @Test
    void theClassInitialiserCannotThrow() throws IOException {
        String classInitialiser = classInitialiserOf(bukkitEntryPointSource());

        int guard = classInitialiser.indexOf("try {");
        assertTrue(guard >= 0 && guard < classInitialiser.indexOf("ECoreDependencies.initialize()"),
                "everything the class initializer does is guarded: " + classInitialiser);
        assertTrue(classInitialiser.contains("catch (Throwable"),
                "an Error is exactly what a broken dependency download throws, and catching Exception "
                        + "would let it through: " + classInitialiser);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading the entry point
    // -----------------------------------------------------------------------------------------------------------------

    /** The {@code static { ... }} block, braces matched, so a {@code try} inside it counts as inside. */
    private static String classInitialiserOf(String source) {
        int start = source.indexOf("static {");
        assertTrue(start >= 0, "the Bukkit entry point has no class initializer at all");
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open, index + 1);
            }
        }
        throw new AssertionError("The class initializer of the Bukkit entry point is not brace-balanced.");
    }

    private static int occurrencesOf(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    private static String bukkitEntryPointSource() throws IOException {
        String suffix = "src/main/java/br/com/finalcraft/evernifecore/minecraft/loader/"
                + "EverNifeCoreBukkitPlugin.java";
        for (String candidate : Arrays.asList(suffix, "minecraft/" + suffix, "../minecraft/" + suffix)) {
            Path path = Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
            if (Files.isRegularFile(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("The Bukkit entry point source was not found from ["
                + System.getProperty("user.dir") + "]");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading what was logged
    // -----------------------------------------------------------------------------------------------------------------

    private CapturedLog captureLog() {
        CapturedLog log = new CapturedLog("BootstrapTest_" + UNIQUE_SUFFIX.incrementAndGet());
        captured.add(log);
        return log;
    }

    /** Holds the logger too: a JDK logger nobody references may be collected, handlers and all. */
    private static final class CapturedLog {

        private final String pluginName;
        private final Logger logger;
        private final Handler handler;
        private final List<String> messages = new ArrayList<>();
        private final boolean usedParentHandlers;

        private CapturedLog(String pluginName) {
            this.pluginName = pluginName;
            this.logger = Logger.getLogger("DependencyManager_" + pluginName);
            this.usedParentHandlers = logger.getUseParentHandlers();
            this.handler = new Handler() {

                @Override
                public void publish(LogRecord record) {
                    messages.add(record.getMessage());
                }

                @Override
                public void flush() {

                }

                @Override
                public void close() {

                }

            };
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
        }

        private List<String> getMessages() {
            return new ArrayList<>(messages);
        }

        private void detach() {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(usedParentHandlers);
        }

    }

}
