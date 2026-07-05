package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.minecraft.listeners.bossshop.BossShopListener;
import br.com.finalcraft.evernifecore.minecraft.metrics.Metrics;
import br.com.finalcraft.evernifecore.minecraft.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
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
        for (Plugin plugin : Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(plugin -> plugin.isEnabled())
                .collect(Collectors.toList())) {
            onPluginEnable(new PluginEnableEvent(plugin));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        final Plugin plugin = event.getPlugin();
        if (plugin.getName().equalsIgnoreCase("BossShopPro")){
            if (NMSUtils.get() != null){
                EverNifeCore.getLog().info("Found BossShopPro, registering 'nbt' tag!");
                ECListener.register(EverNifeCore.getEcPluginData(), BossShopListener.class);
            }else {
                EverNifeCore.getLog().warning("Found BossShopPro, but NMS not found for this server version [" + MCVersion.getCurrent() +" ] !");
            }
            return;
        }

        ECPlugin ecPlugin = plugin.getClass().getAnnotation(ECPlugin.class);
        if (ecPlugin != null){
            //Enable BStats for this plugin
            if (!ecPlugin.bstatsID().isEmpty()){
                new Metrics(plugin, Integer.parseInt(ecPlugin.bstatsID()));
            }

            //Enable Automatic Spigot Update for this plugin
            if (!ecPlugin.spigotID().isEmpty()){
                ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);
                SpigotUpdateChecker.checkForUpdates((JavaPlugin) plugin, ecPlugin.spigotID(), ConfigFactory.open(ecPluginData, "config.yml"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        /*
         * This will remove the data from this plugin if it was a ECPlugin
         *
         * Will remove data Like "TabCompletion" and "Localization" from the memory cache
         */
        String pluginName = event.getPlugin().getName();

        //unregister the plugin's PDSections BEFORE its ECPluginData is dropped: a runtime-disabled
        //plugin must not leave bindings pinning its classes/classloader (and a later re-enable
        //re-registers with a fresh Class object, which would collide with the stale claim)
        ECPluginData ecPluginData = ECPluginManager.getECPluginsMap().get(pluginName);
        if (ecPluginData != null){
            PlayerController.unregisterPDSections(ecPluginData);
        }

        ECPluginManager.removePluginData(pluginName);
    }


}
