package br.com.finalcraft.evernifecore.ecplugin;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class ECPluginManager {

    private static final HashMap<String, ECPlugin> ECPLUGINS_MAP = new HashMap<>();

    @NotNull
    public static ECPlugin getOrCreateECorePlugin(Plugin plugin){
        return ECPLUGINS_MAP.computeIfAbsent(plugin.getName(), pluginName -> new ECPlugin(plugin));
    }

    public static void removePluginData(String playerName){
        ECPLUGINS_MAP.remove(playerName);
    }

}

