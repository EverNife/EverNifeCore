package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.everyconfig.config.Config;

import java.io.File;

/**
 * A plugin that exists only for the test: a name, a data folder, and enough metadata for
 * {@code ECPluginManager} to build an {@code ECPluginData} around it.
 *
 * <p>The same ~70 lines used to sit inside four locale test classes and the command harness.</p>
 */
public final class Plugins {

    private Plugins() {
    }

    /** An extractor that answers for a single fake plugin, whatever object is handed to it. */
    public static IECPluginExtractor fake(String pluginName, File dataFolder) {
        return new FakePluginExtractor(pluginName, dataFolder);
    }

    /**
     * Makes {@code plugin} speak {@code language}, through the very file an operator would edit, and
     * has it read the change back on the spot.
     *
     * <p>Call it before anything asks the plugin for a text: the language decides which comment is
     * seeded, which language block answers a viewer who chose none, and which file the translations
     * are read from.</p>
     */
    public static void setLanguage(ECPluginData plugin, String language) {
        File file = new File(plugin.getMetaInfo().getDataFolder(), "localization/localization_config.yml");
        file.getParentFile().mkdirs();
        Config config = ConfigFactory.open(plugin, file);
        config.setValue("Localization.fileName", "lang_" + LocaleType.normalize(language) + ".yml");
        config.save();
        plugin.reloadAllCustomLocales();
    }

    private static final class FakePluginExtractor implements IECPluginExtractor {
        private final String pluginName;
        private final File dataFolder;

        FakePluginExtractor(String pluginName, File dataFolder) {
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            return pluginName;
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            return true;
        }

        @Override
        public Object getProvidingPlugin(Class<?> clazz) {
            return null;
        }

        @Override
        public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
            return new FakeMetaInfo(javaPlugin, pluginName, dataFolder);
        }
    }

    private static final class FakeMetaInfo implements IPluginMetaInfo {
        private final Object plugin;
        private final String pluginName;
        private final File dataFolder;

        FakeMetaInfo(Object plugin, String pluginName, File dataFolder) {
            this.plugin = plugin;
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getName() {
            return pluginName;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getAuthor() {
            return "Petrus";
        }

        @Override
        public String getGroup() {
            return "br.com.finalcraft";
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }

        @Override
        public Object getDelegate() {
            return plugin;
        }
    }
}
