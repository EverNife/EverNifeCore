package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.EverNifeCoreReloadEvent;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginPreReloadEvent;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class ECPluginManager {

    private static final HashMap<String, ECPlugin> ECPLUGINS_MAP = new HashMap<>();

    @NotNull
    public static ECPlugin getOrCreateECorePlugin(Plugin plugin){
        return ECPLUGINS_MAP.computeIfAbsent(plugin.getName(), pluginName -> new ECPlugin(plugin));
    }

    public static void reloadPlugin(@Nullable CommandSender sender, @NotNull Plugin instance, @NotNull Runnable runnable){

        ECPlugin ecPlugin = getOrCreateECorePlugin(instance);
        //Fire Pre-Reload
        //Mainly used for Plugins that has other addons or modules
        Bukkit.getPluginManager().callEvent(new ECPluginPreReloadEvent(ecPlugin));

        long start = System.currentTimeMillis();

        //Do the reload
        runnable.run();
        //Reload locales as well
        ecPlugin.reloadAllCustomLocales();

        long end = System.currentTimeMillis();

        //Notify the Console
        ecPlugin.getPlugin().getLogger().info("§e[Reloading] §a" + ecPlugin.getPlugin().getName() + " has been reloaded! §7(It took " + new FCTimeFrame(end - start).getFormatedDiscursive(true) + ")");

        //Notify the sender if it's a Player
        if (sender != null && sender instanceof Player == true){
            FCMessageUtil.pluginHasBeenReloaded(sender, instance.getName());
        }

        Bukkit.getPluginManager().callEvent(new ECPluginReloadEvent(ecPlugin));

        //If it's the EverNifeCore, call it's personal reload event
        if (ecPlugin.getPlugin() == EverNifeCore.instance){
            Bukkit.getPluginManager().callEvent(new EverNifeCoreReloadEvent(ecPlugin));
        }
    }

    public static void removePluginData(String playerName){
        ECPLUGINS_MAP.remove(playerName);
    }

    public static HashMap<String, ECPlugin> getECPluginsMap() {
        return ECPLUGINS_MAP;
    }
}

