package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.minecraft.metrics.Metrics;
import br.com.finalcraft.evernifecore.minecraft.protection.ProtectionAll;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.ProtectionHandler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PluginListener implements ECListener {

    /** The handler EverNifeCore registered for each protection plugin it ships one for, by plugin name. */
    private final Map<String, ProtectionHandler> builtInProtections = new HashMap<>();

    @Override
    public void onRegister() {
        onRegister(Bukkit.getPluginManager());
    }

    /**
     * Replays the enable of every plugin already enabled, and the disable of every plugin that died before this
     * listener could hear it: one Bukkit enabled ahead of the core ({@code getPlugins()} is the enable order) and that
     * is disabled now. A plugin after the core is not disabled, only not enabled yet - what it built during its load
     * stays - and a name Bukkit does not know is not a Bukkit plugin to reconcile.
     */
    void onRegister(PluginManager pluginManager) {
        Plugin[] plugins = pluginManager.getPlugins();
        String coreName = EverNifeCore.getEcPluginData().getMetaInfo().getName();
        Map<String, ECPluginData> known = ECPluginManager.getECPluginsMap();
        for (Plugin plugin : plugins) {
            if (plugin.getName().equals(coreName)) {
                break;
            }
            if (!plugin.isEnabled() && known.containsKey(plugin.getName())) {
                forget(plugin.getName());
            }
        }
        for (Plugin plugin : Arrays.stream(plugins)
                .filter(plugin -> plugin.isEnabled())
                .collect(Collectors.toList())) {
            onPluginEnable(new PluginEnableEvent(plugin));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        final Plugin plugin = event.getPlugin();

        //the handler arrives with its plugin, whenever that enables - before or after this core
        ProtectionHandler builtIn = builtInProtections.containsKey(plugin.getName()) ? null : ProtectionAll.builtInFor(plugin.getName());
        if (builtIn != null) {
            builtInProtections.put(plugin.getName(), builtIn);
            ProtectionAll.getInstance().register(EverNifeCore.getEcPluginData(), builtIn);
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

    /**
     * Bukkit posts this BEFORE it calls the plugin's {@code onDisable}. A plugin that runs its own shutdown
     * ({@link IECPluginBootstrap#runECPluginShutdown()}) still needs its storage-backed state then, and forgets itself at
     * the end of it; any other plugin is forgotten here. The core's built-in protection handler for the plugin leaves
     * either way.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        String pluginName = event.getPlugin().getName();
        forgetBuiltInProtection(pluginName);
        ECPluginData ecPluginData = ECPluginManager.getECPluginsMap().get(pluginName);
        if (ecPluginData == null || !(ecPluginData.getPlugin() instanceof IECPluginBootstrap)) {
            ECPluginManager.removePluginData(pluginName);
        }
    }

    /** A plugin dead before this listener: its shutdown, if it had one, is already over. */
    private void forget(String pluginName) {
        forgetBuiltInProtection(pluginName);
        //its sections, protection handlers and placeholder expansions leave with it (ECPluginManager and McPlatform)
        ECPluginManager.removePluginData(pluginName);
    }

    private void forgetBuiltInProtection(String pluginName) {
        ProtectionHandler builtIn = builtInProtections.remove(pluginName);
        if (builtIn != null) {
            ProtectionAll.getInstance().unregister(builtIn);
        }
    }
}
