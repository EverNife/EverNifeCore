package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.everyconfig.config.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A plugin that exists only for the test: a name, a data folder, and enough metadata for
 * {@code ECPluginManager} to build an {@code ECPluginData} around it.
 *
 * <p>The same ~70 lines used to sit inside four locale test classes and the command harness.</p>
 */
public final class Plugins {

    /** The name a fake EverNifeCore answers to - the owner the core flags' keys name, once lowercased. */
    public static final String CORE = "EverNifeCore";

    private Plugins() {
    }

    /** An extractor that answers for a single fake plugin, whatever object is handed to it. */
    public static IECPluginExtractor fake(String pluginName, File dataFolder) {
        return new FakePluginExtractor(pluginName, dataFolder, null);
    }

    /**
     * As {@link #fake(String, File)}, but only {@code thePlugin} counts as a plugin - the way a real
     * extractor answers {@code plugin instanceof JavaPlugin}. Anything else is refused by
     * {@code IECPluginExtractor#validateJavaPlugin}, which is what registering a stray object looks
     * like on a server.
     */
    public static IECPluginExtractor fakeRecognisingOnly(String pluginName, File dataFolder, Object thePlugin) {
        return new FakePluginExtractor(pluginName, dataFolder, thePlugin);
    }

    /**
     * An extractor that knows several fake plugins and tells which one provides a class by its package, the way a
     * server tells which jar a class came from - what a test of one plugin mounting another plugin's code needs.
     */
    public static Routing routing() {
        return new Routing();
    }

    /**
     * The {@link ECPluginData} of a plugin that exists only for the test - {@link #fake(String, File)}
     * installed as the extractor and the data built through {@code ECPluginManager}, so it owns
     * subscriptions, watches, locales and everything else a real plugin does. Needs a platform installed
     * ({@code @ECoreTest}). {@link #forget(String)} removes it again; a test that creates one owes that call.
     */
    public static ECPluginData fakePluginData(String pluginName, File dataFolder) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, fake(pluginName, dataFolder));
        return ECPluginManager.getOrCreateECorePluginData(new Object());
    }

    /**
     * A fake EverNifeCore ({@link #CORE}), the owner the core flags register under. Unlike
     * {@link #fakePluginData(String, File)} it leaves the extractor already installed answering for classes, so it can
     * be built at any point of a setup. {@code forget(Plugins.CORE)} removes it again.
     */
    public static ECPluginData fakeCore(File dataFolder) {
        IECPluginExtractor previous = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class);
        ECPluginData core = fakePluginData(CORE, dataFolder);
        if (previous != null) {
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, previous);
        }
        return core;
    }

    /** Removes the plugin data {@link #fakePluginData(String, File)} built under {@code pluginName}. */
    public static void forget(String pluginName) {
        ECPluginManager.removePluginData(pluginName);
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

    /** See {@link #routing()}. Each plugin is answered by a token object of its own. */
    public static final class Routing implements IECPluginExtractor {

        private final List<Route> routes = new ArrayList<>();

        private Routing() {
        }

        /**
         * Declares a plugin that provides every class whose name starts with one of {@code packagePrefixes}. When
         * prefixes of two plugins match, the longest wins; a class no prefix matches belongs to the first plugin.
         */
        public Routing plugin(String name, File dataFolder, String... packagePrefixes) {
            routes.add(new Route(new Object(), name, dataFolder, packagePrefixes));
            return this;
        }

        /** The object that stands for the plugin {@code name}, as {@code getProvidingPlugin} hands it out. */
        public Object getToken(String name) {
            for (Route route : routes) {
                if (route.name.equals(name)) {
                    return route.token;
                }
            }
            throw new IllegalArgumentException("No plugin [" + name + "] was declared - add it with plugin(name, dir, prefixes...)");
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            return route(javaPlugin).name;
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            for (Route route : routes) {
                if (route.token == plugin) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object getProvidingPlugin(Class<?> clazz) {
            if (routes.isEmpty()) {
                throw new IllegalStateException("Plugins.routing() knows no plugin - declare one with plugin(name, dir, prefixes...)");
            }
            Route best = routes.get(0);
            int bestLength = -1;
            for (Route route : routes) {
                for (String prefix : route.prefixes) {
                    if (clazz.getName().startsWith(prefix) && prefix.length() > bestLength) {
                        best = route;
                        bestLength = prefix.length();
                    }
                }
            }
            return best.token;
        }

        @Override
        public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
            Route route = route(javaPlugin);
            return new FakeMetaInfo(javaPlugin, route.name, route.dataFolder);
        }

        private Route route(Object token) {
            for (Route route : routes) {
                if (route.token == token) {
                    return route;
                }
            }
            throw new IllegalArgumentException("[" + token + "] is not a plugin this routing extractor declared");
        }

        private static final class Route {
            final Object token;
            final String name;
            final File dataFolder;
            final String[] prefixes;

            Route(Object token, String name, File dataFolder, String[] prefixes) {
                this.token = token;
                this.name = name;
                this.dataFolder = dataFolder;
                this.prefixes = prefixes;
            }
        }
    }

    private static final class FakePluginExtractor implements IECPluginExtractor {
        private final String pluginName;
        private final File dataFolder;
        private final Object recognised;

        FakePluginExtractor(String pluginName, File dataFolder, Object recognised) {
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
            this.recognised = recognised;
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            return pluginName;
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            //no object was singled out: this extractor speaks for whatever it is handed
            return recognised == null || recognised == plugin;
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
