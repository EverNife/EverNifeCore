package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.EverNifeCoreReloadEvent;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginPreReloadEvent;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.sound.FCSound;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class ECPluginManager {

    /**
     * Map that holds all EverNifeCore Plugins that are using its features like localization or logging.
     */
    private static final HashMap<String, ECPluginData> EVERNIFECORE_PLUGINS_MAP = new LinkedHashMap<>();

    @Nonnull
    public static ECPluginData getOrCreateECorePluginData(Plugin plugin){
        return EVERNIFECORE_PLUGINS_MAP.computeIfAbsent(plugin.getName(), pluginName -> new ECPluginData(plugin));
    }

    public static void reloadPlugin(@Nullable CommandSender sender, @Nonnull Plugin instance) {
        ECPluginData ecPluginData = getOrCreateECorePluginData(instance);
        if (!ecPluginData.canReload()){
            throw new IllegalStateException(String.format(
                    "The plugin [%s] does not implement a '@ECPlugin.Reload' System on it! Tell the author (%s) !",
                    instance.getName(),
                    instance.getDescription().getAuthors().isEmpty() ? "Unknown" : instance.getDescription().getAuthors().get(0)
            ));
        }
        reloadPlugin(sender, instance, ecPluginData, () -> ecPluginData.reloadPlugin());
    }

    public static void reloadPlugin(@Nullable CommandSender sender, @Nonnull Plugin instance, @Nonnull Runnable runnable){
        reloadPlugin(sender, instance, getOrCreateECorePluginData(instance), runnable);
    }

    public static void reloadPlugin(@Nullable CommandSender sender, @Nonnull Plugin instance, ECPluginData ecPluginData, @Nonnull Runnable runnable){
        //Fire Pre-Reload
        //Mainly used for Plugins that has other addons or modules
        Bukkit.getPluginManager().callEvent(new ECPluginPreReloadEvent(ecPluginData));

        long start = System.currentTimeMillis();

        ecPluginData.setDebugEnabled(null); //By setting to null, will 're-check' the config.yml for the debug value when needed

        //Do the reload
        runnable.run();
        //Reload locales as well
        ecPluginData.reloadAllCustomLocales();

        long end = System.currentTimeMillis();

        //Notify the Console
        ecPluginData.getPlugin().getLogger().info("§e[Reloading] §a" + ecPluginData.getPlugin().getName() + " has been reloaded! §7(It took " + FCTimeFrame.of(end - start).getFormattedDiscursive(true) + ")");

        //Notify the sender if it's a Player
        if (sender != null && sender instanceof Player == true){
            FCMessageUtil.pluginHasBeenReloaded(sender, instance.getName());
            FCSound.LEVEL_UP.playSoundFor((Player) sender);
        }

        Bukkit.getPluginManager().callEvent(new ECPluginReloadEvent(ecPluginData));

        //Some ECPlugins might have subModules or Addons, reload them if necessary
        for (ECPluginData ecPlugin : new ArrayList<>(EVERNIFECORE_PLUGINS_MAP.values())) {
            if (ecPlugin.canReload()){
                for (String pluginName : ecPlugin.getReloadAfter()) {
                    if (instance.getName().equalsIgnoreCase(pluginName)){
                        ecPlugin.getPlugin().getLogger().info("[ECPlugin] Reloading by demand of ´" + instance.getName() + "´.");
                        ecPlugin.reloadPlugin();
                    }
                }
            }
        }

        //If it's the EverNifeCore, call it's personal reload event
        if (ecPluginData.getPlugin() == EverNifeCore.instance){
            Bukkit.getPluginManager().callEvent(new EverNifeCoreReloadEvent(ecPluginData));
        }
    }

    public static void removePluginData(String pluginName){
        EVERNIFECORE_PLUGINS_MAP.remove(pluginName);
    }

    public static HashMap<String, ECPluginData> getECPluginsMap() {
        return EVERNIFECORE_PLUGINS_MAP;
    }
}

