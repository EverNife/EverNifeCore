package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a lookup of an already known plugin costs, and what it is allowed to remember.
 *
 * <p>Every log line reaches {@link ECPluginManager#getOrCreateECorePluginData(Object)}, so whatever
 * that asks the platform is paid per line and not per plugin. The object handed in is the one key
 * that costs nothing to compute - and the reason the answer to it may never outlive the registry
 * entry it points at: a shortcut that keeps answering for a plugin the registry no longer lists is
 * worse than the lookup it saved.</p>
 */
class ECPluginDataCreationTest {

    private static final String PLUGIN_NAME = "CreationTest";

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aKnownPluginObjectIsAnsweredWithoutAskingThePlatformAnything() {
        Object plugin = new Object();
        CountingExtractor extractor = countingExtractor();

        try (ECoreTestWorld world = worldWith(extractor)) {
            try {
                ECPluginData first = ECPluginManager.getOrCreateECorePluginData(plugin);
                int questionsItTookToBuildIt = extractor.questionsAsked();

                ECPluginData second = ECPluginManager.getOrCreateECorePluginData(plugin);

                assertSame(first, second, "the same object got two different plugin datas");
                assertEquals(1, extractor.pluginDataBuilt(), "the data was built more than once");
                assertEquals(questionsItTookToBuildIt, extractor.questionsAsked(),
                        "a lookup of an already known plugin went back to the platform");
            } finally {
                ECPluginManager.removePluginData(PLUGIN_NAME);
            }
        }
    }

    @Test
    void aPluginDroppedFromTheRegistryIsBuiltAgainAndListedAgain() {
        Object plugin = new Object();
        CountingExtractor extractor = countingExtractor();

        try (ECoreTestWorld world = worldWith(extractor)) {
            try {
                ECPluginData first = ECPluginManager.getOrCreateECorePluginData(plugin);
                ECPluginManager.removePluginData(PLUGIN_NAME);

                ECPluginData rebuilt = ECPluginManager.getOrCreateECorePluginData(plugin);

                assertNotSame(first, rebuilt, "the data dropped from the registry was handed out again");
                assertEquals(2, extractor.pluginDataBuilt(), "the rebuild did not happen");
                assertSame(rebuilt, ECPluginManager.getECPluginsMap().get(PLUGIN_NAME),
                        "the data answering lookups is not the one the registry lists");
            } finally {
                ECPluginManager.removePluginData(PLUGIN_NAME);
            }
        }
    }

    /**
     * Two objects answering to one plugin name is what a plugin re-instantiated at runtime looks
     * like. The name is what the registry keys on, so both are the same plugin - and dropping it
     * takes down the shortcut of the object it was NOT built around just the same.
     */
    @Test
    void twoObjectsUnderOneNameShareTheDataAndLoseItTogether() {
        Object built = new Object();
        Object alsoTheSamePlugin = new Object();
        CountingExtractor extractor = countingExtractor();

        try (ECoreTestWorld world = worldWith(extractor)) {
            try {
                ECPluginData data = ECPluginManager.getOrCreateECorePluginData(built);
                assertSame(data, ECPluginManager.getOrCreateECorePluginData(alsoTheSamePlugin),
                        "one plugin name is one plugin");

                ECPluginManager.removePluginData(PLUGIN_NAME);

                assertNotSame(data, ECPluginManager.getOrCreateECorePluginData(alsoTheSamePlugin),
                        "the object the data was not built around kept answering with the dropped data");
            } finally {
                ECPluginManager.removePluginData(PLUGIN_NAME);
            }
        }
    }

    /** The listing is a read-only snapshot, so removePluginData stays the only way out of the registry. */
    @Test
    void thePluginListingCannotDropAPlugin() {
        Object plugin = new Object();

        try (ECoreTestWorld world = worldWith(countingExtractor())) {
            try {
                ECPluginData data = ECPluginManager.getOrCreateECorePluginData(plugin);

                assertThrows(UnsupportedOperationException.class,
                        () -> ECPluginManager.getECPluginsMap().remove(PLUGIN_NAME));
                assertSame(data, ECPluginManager.getECPluginsMap().get(PLUGIN_NAME));
            } finally {
                ECPluginManager.removePluginData(PLUGIN_NAME);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers and fixtures
    // -----------------------------------------------------------------------------------------------------------------

    private CountingExtractor countingExtractor() {
        return new CountingExtractor(Plugins.fake(PLUGIN_NAME, tempDir.toFile()));
    }

    private ECoreTestWorld worldWith(IECPluginExtractor extractor) {
        return Platforms.strict().loggingToStdout().ignoringPlatformRegistrations().install()
                .withPluginExtractor(extractor);
    }

    /** Answers exactly like the extractor it wraps, and remembers how often it was asked. */
    private static final class CountingExtractor implements IECPluginExtractor {

        private final IECPluginExtractor delegate;
        private int questionsAsked;
        private int pluginDataBuilt;

        CountingExtractor(IECPluginExtractor delegate) {
            this.delegate = delegate;
        }

        /** Everything a lookup asks the platform before it can name the plugin. */
        int questionsAsked() {
            return questionsAsked;
        }

        /** The metadata is read once per {@link ECPluginData} constructed, so this counts creations. */
        int pluginDataBuilt() {
            return pluginDataBuilt;
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            questionsAsked++;
            return delegate.getPluginName(javaPlugin);
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            questionsAsked++;
            return delegate.isJavaPlugin(plugin);
        }

        @Override
        public Object getProvidingPlugin(Class<?> clazz) {
            return delegate.getProvidingPlugin(clazz);
        }

        @Override
        public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
            pluginDataBuilt++;
            return delegate.getPluginMetaInfo(javaPlugin);
        }
    }
}
