package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ECPluginManager {

    /**
     * Map that holds all EverNifeCore Plugins that are using its features like localization or logging.
     */
    private static final Map<String, ECPluginData> EVERNIFECORE_PLUGINS_MAP = new LinkedHashMap<>();

    /**
     * The same data, keyed by the plugin object that was handed in. It answers before the platform is
     * asked to validate the object and to spell its name - the price a log line used to pay on every
     * single call. It never holds an entry {@link #EVERNIFECORE_PLUGINS_MAP} does not.
     */
    private static final Map<Object, ECPluginData> PLUGIN_DATA_BY_IDENTITY = new IdentityHashMap<>();

    /**
     * The {@link ECPluginData} of {@code plugin}, built the first time this object is seen and read
     * back from memory afterwards - only that first call asks the platform anything.
     */
    @Nonnull
    public static synchronized ECPluginData getOrCreateECorePluginData(Object plugin){
        if (plugin instanceof ECPluginData){
            return (ECPluginData) plugin;
        }

        ECPluginData known = PLUGIN_DATA_BY_IDENTITY.get(plugin);
        if (known != null){
            return known;
        }

        IECPluginExtractor ecPluginExtractor = EverNifeCore.instance
                .getProviders()
                .getECPluginExtractor();

        ecPluginExtractor.validateJavaPlugin(plugin);

        ECPluginData ecPluginData = EVERNIFECORE_PLUGINS_MAP.computeIfAbsent(
                ecPluginExtractor.getPluginName(plugin),
                pluginName -> new ECPluginData(plugin)
        );

        PLUGIN_DATA_BY_IDENTITY.put(plugin, ecPluginData);
        return ecPluginData;
    }

    public static ECPluginData getProvidingPlugin(@Nonnull Class<?> clazz) {
        Object providingPlugin = EverNifeCore.instance
                .getProviders()
                .getECPluginExtractor()
                .getProvidingPlugin(clazz);

        return getOrCreateECorePluginData(providingPlugin);
    }

    public static void reloadPlugin(@Nullable FCommandSender sender, @Nonnull ECPluginData ecPluginData) {
        if (!ecPluginData.canReload()){
            throw new IllegalStateException(String.format(
                    "The plugin [%s] does not implement a '@ECPlugin.Reload' System on it! Tell the author (%s) !",
                    ecPluginData.getMetaInfo().getName(),
                    ecPluginData.getMetaInfo().getAuthor()
            ));
        }

        reloadPlugin(sender, ecPluginData, () -> ecPluginData.reloadPlugin());
    }

    public static void reloadPlugin(@Nullable FCommandSender sender, ECPluginData ecPluginData, @Nonnull Runnable runnable){
        //Fire Pre-Reload
        //Mainly used for Plugins that has other addons or modules
        long start = System.currentTimeMillis();

        //up front, so the debug lines the reload itself emits already obey the edited file
        ecPluginData.loadDebugConfig();

        //Fire Pre-Reload before the reload runs, so listeners observe the old state
        EverNifeCore.getEventBus().post(new ECPluginReloadEvent.Pre(ecPluginData));

        //Do the reload
        runnable.run();
        //Reload locales as well
        ecPluginData.reloadAllCustomLocales();

        long end = System.currentTimeMillis();

        //Notify the Console
        ecPluginData.getLog().info("§e[Reloading] §a" + ecPluginData.getMetaInfo().getName() + " has been reloaded! §7(It took " + FCTimeFrame.of(end - start).getFormattedDiscursive(true) + ")");

        //Notify the sender if it's a Player
        if (sender != null) {
            FCMessageUtil.pluginHasBeenReloaded(sender, ecPluginData.getMetaInfo().getName());
//            FCSound.LEVEL_UP.playSoundFor((Player) sender);
        }

        //Some ECPlugins might have subModules or Addons that opt to reload after this one; reload them if necessary
        for (ECPluginData ecPlugin : getECPluginsMap().values()) {
            if (ecPlugin.canReload()){
                for (String pluginName : ecPlugin.getReloadAfter()) {
                    if (ecPluginData.getMetaInfo().getName().equalsIgnoreCase(pluginName)){
                        ecPlugin.getLog().info("[ECPlugin] Reloading by demand of '" + ecPluginData.getMetaInfo().getName() + "'.");
                        ecPlugin.reloadPlugin();
                    }
                }
            }
        }

        EverNifeCore.getEventBus().post(new ECPluginReloadEvent.Post(ecPluginData));
    }

    /**
     * Drops this plugin from the registry together with every shortcut pointing at its data: the
     * identity cache entries and whatever the plugin objects themselves cached
     * ({@link IECPluginBootstrap#clearCachedPluginData()}). Asked for again afterwards, the plugin
     * gets fresh data - the same answer it got before it was ever registered.
     */
    public static synchronized void removePluginData(String pluginName){
        ECPluginData removed = EVERNIFECORE_PLUGINS_MAP.remove(pluginName);
        if (removed == null){
            return;
        }

        //by value, not by removed.getPlugin(): a plugin re-instantiated at runtime leaves two objects
        //answering to one name, and both were handed this very data
        List<Object> orphaned = new ArrayList<>();
        for (Map.Entry<Object, ECPluginData> entry : PLUGIN_DATA_BY_IDENTITY.entrySet()) {
            if (entry.getValue() == removed){
                orphaned.add(entry.getKey());
            }
        }

        for (Object plugin : orphaned) {
            PLUGIN_DATA_BY_IDENTITY.remove(plugin);
            if (plugin instanceof IECPluginBootstrap){
                ((IECPluginBootstrap) plugin).clearCachedPluginData();
            }
        }
    }

    /**
     * Every registered plugin by name, in registration order. A read-only snapshot: the registry is
     * the only place a plugin enters or leaves from, because dropping one has to take its cached
     * shortcuts down with it ({@link #removePluginData(String)}).
     */
    public static synchronized Map<String, ECPluginData> getECPluginsMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(EVERNIFECORE_PLUGINS_MAP));
    }
}

