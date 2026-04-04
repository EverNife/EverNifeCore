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
import java.util.HashMap;
import java.util.LinkedHashMap;

public class ECPluginManager {

    /**
     * Map that holds all EverNifeCore Plugins that are using its features like localization or logging.
     */
    private static final HashMap<String, ECPluginData> EVERNIFECORE_PLUGINS_MAP = new LinkedHashMap<>();

    @Nonnull
    public static ECPluginData getOrCreateECorePluginData(Object plugin){
        if (plugin instanceof ECPluginData){
            return (ECPluginData) plugin;
        }

        IECPluginExtractor ecPluginExtractor = EverNifeCore.instance
                .getProviders()
                .getECPluginExtractor();

        ecPluginExtractor.validateJavaPlugin(plugin);

        return EVERNIFECORE_PLUGINS_MAP.computeIfAbsent(
                ecPluginExtractor.getPluginName(plugin),
                pluginName -> new ECPluginData(plugin)
        );
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
                    ecPluginData.getPluginData().getName(),
                    ecPluginData.getPluginData().getAuthor()
            ));
        }

        reloadPlugin(sender, ecPluginData, () -> ecPluginData.reloadPlugin());
    }

    public static void reloadPlugin(@Nullable FCommandSender sender, ECPluginData ecPluginData, @Nonnull Runnable runnable){
        //Fire Pre-Reload
        //Mainly used for Plugins that has other addons or modules
        long start = System.currentTimeMillis();

        ecPluginData.setDebugEnabled(null); //By setting to null, will 're-check' the config.yml for the debug value when needed

        //Do the reload
        runnable.run();
        //Reload locales as well
        ecPluginData.reloadAllCustomLocales();

        long end = System.currentTimeMillis();

        //Notify the Console
        ecPluginData.getLog().info("§e[Reloading] §a" + ecPluginData.getPluginData().getName() + " has been reloaded! §7(It took " + FCTimeFrame.of(end - start).getFormattedDiscursive(true) + ")");

        //Notify the sender if it's a Player
        if (sender != null) {
            FCMessageUtil.pluginHasBeenReloaded(sender, ecPluginData.getPluginData().getName());
//            FCSound.LEVEL_UP.playSoundFor((Player) sender);
        }

        EverNifeCore.instance.getProviders()
                .getEventDispatcher()
                .post(new ECPluginReloadEvent.Pre(ecPluginData));

        //Some ECPlugins might have subModules or Addons, reload them if necessary
        for (ECPluginData ecPlugin : new ArrayList<>(EVERNIFECORE_PLUGINS_MAP.values())) {
            if (ecPlugin.canReload()){
                for (String pluginName : ecPlugin.getReloadAfter()) {
                    if (ecPlugin.getPluginData().getName().equalsIgnoreCase(pluginName)){
                        ecPlugin.getLog().info("[ECPlugin] Reloading by demand of ´" + ecPlugin.getPluginData().getName() + "´.");
                        ecPlugin.reloadPlugin();
                    }
                }
            }
        }

        EverNifeCore.instance.getProviders()
                .getEventDispatcher()
                .post(new ECPluginReloadEvent.Post(ecPluginData));
    }

    public static void removePluginData(String pluginName){
        EVERNIFECORE_PLUGINS_MAP.remove(pluginName);
    }

    public static HashMap<String, ECPluginData> getECPluginsMap() {
        return EVERNIFECORE_PLUGINS_MAP;
    }
}

