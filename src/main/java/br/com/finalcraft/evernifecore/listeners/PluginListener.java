package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.bossshop.BossShopListener;
import br.com.finalcraft.evernifecore.metrics.Metrics;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PluginListener implements ECListener {

    @Override
    public void onRegister() {
        for (Plugin plugin : Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(plugin -> plugin.isEnabled()).collect(Collectors.toList())) {
            onPluginEnable(new PluginEnableEvent(plugin));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        final Plugin plugin = event.getPlugin();
        if (plugin.getName().equalsIgnoreCase("BossShopPro")){
            if (NMSUtils.get() != null){
                EverNifeCore.info("Found BossShopPro, registering 'nbt' tag!");
                ECListener.register(EverNifeCore.instance, BossShopListener.class);
            }else {
                EverNifeCore.info("Found BossShopPro, but NMS not found for this server version [" + MCVersion.getCurrent() +" ] !");
            }
            return;
        }

        ECPlugin ecPlugin = plugin.getClass().getAnnotation(ECPlugin.class);
        if (ecPlugin != null){
            //Enable BStats for this plugin
            if (!ecPlugin.bstatsID().isEmpty()){
                new Metrics((JavaPlugin) plugin, Integer.parseInt(ecPlugin.bstatsID()));
            }

            //Enable Automatic Spigot Update for this plugin
            if (!ecPlugin.spigotID().isEmpty()){
                SpigotUpdateChecker.checkForUpdates((JavaPlugin) plugin, ecPlugin.spigotID(), new Config(plugin, "config.yml"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        /*
         * This will remove the data from this plugin if it was a ECPlugin
         *
         * Data Like "TabCompletion" and "Localization" from cache
         */
        ECPluginManager.removePluginData(event.getPlugin().getName());
    }


}
