package br.com.finalcraft.evernifecore.minecraft.consolefilter;

import br.com.finalcraft.evernifecore.consolefilter.ECConsoleFilter;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Log4j hook denies what the registered provider hides, and asks the provider in place now, not the one it was
 * built with; its undo removes it again on the log4j 1.7.10 ships as well as on the current one.
 */
@ECoreTest
class ECBukkitConsoleFilterTest {

    private static LogEvent line(String text) {
        return Log4jLogEvent.newBuilder().setMessage(new SimpleMessage(text)).build();
    }

    @Test
    void theHookDeniesWhatTheProviderHides() {
        ECConsoleFilter consoleFilter = new ECConsoleFilter();
        ECBukkitConsoleFilter hook = new ECBukkitConsoleFilter(consoleFilter);

        assertEquals(Filter.Result.DENY, hook.filter(line("Steve issued server command: /ecdcmd 4f1c2b6e")));
        assertEquals(Filter.Result.NEUTRAL, hook.filter(line("Steve issued server command: /spawn")));

        consoleFilter.setProvider(message -> message.contains("/spawn"));

        assertEquals(Filter.Result.DENY, hook.filter(line("Steve issued server command: /spawn")),
                "a provider registered after the hook was installed is the one it asks");
    }

    @Test
    void theUndoRemovesTheHookOnTheLog4jOf1_7_10() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String jar : System.getProperty("ecore.test.log4jLegacy").split(File.pathSeparator)) {
            urls.add(new File(jar).toURI().toURL());
        }
        urls.add(ECBukkitConsoleFilter.class.getProtectionDomain().getCodeSource().getLocation());

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        //left open: the old log4j stops its context from a JVM shutdown hook, which still loads classes from it
        Log4jLegacyLoader loader = new Log4jLegacyLoader(urls.toArray(new URL[0]));
        try {
            //log4j looks its provider up through the context class loader
            Thread.currentThread().setContextClassLoader(loader);
            Object rootLogger = loader.loadClass("org.apache.logging.log4j.LogManager").getMethod("getRootLogger").invoke(null);
            Class<?> coreLogger = loader.loadClass("org.apache.logging.log4j.core.Logger");
            assertTrue(coreLogger.getProtectionDomain().getCodeSource().getLocation().getPath().contains("2.0-beta9"), "the 1.7.10 log4j is the one loaded");
            int before = (int) coreLogger.getMethod("filterCount").invoke(rootLogger);

            Runnable undo = (Runnable) loader.loadClass(ECBukkitConsoleFilter.class.getName()).getMethod("install").invoke(null);
            assertEquals(before + 1, (int) coreLogger.getMethod("filterCount").invoke(rootLogger));

            undo.run();
            assertEquals(before, (int) coreLogger.getMethod("filterCount").invoke(rootLogger), "the undo took the hook back off");
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /** Child-first for log4j and the hook, so they link against the old jars; everything else comes from the test JVM. */
    private static final class Log4jLegacyLoader extends URLClassLoader {
        Log4jLegacyLoader(URL[] urls) {
            super(urls, ECBukkitConsoleFilterTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("org.apache.logging.log4j.") && !name.startsWith(ECBukkitConsoleFilter.class.getPackage().getName() + ".")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : findClass(name);
            }
        }

        //log4j finds its provider and plugins by resource: the current log4j's must not answer
        @Override
        public URL getResource(String name) {
            return findResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return findResources(name);
        }
    }
}
