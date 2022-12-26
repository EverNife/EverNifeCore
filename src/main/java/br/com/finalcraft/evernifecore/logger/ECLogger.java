package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;
import java.util.logging.Level;

public class ECLogger {

    private final JavaPlugin plugin;
    private transient ECPluginData ecPluginData = null;

    public ECLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ECPluginData getEcPluginData() {
        if (ecPluginData == null){
            ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);
        }
        return ecPluginData;
    }

    public void log(Level level, String msg) {
        plugin.getLogger().log(level, msg);
    }

    public void log(Level level, Supplier<String> supplier) {
        plugin.getLogger().log(level, supplier);
    }

    public void debug(String message, Object... params) {
        if (getEcPluginData().isDebugEnabled()){
            String formatted = "[Debug] " + (params.length == 0 ? message : String.format(message, params));
            plugin.getLogger().info(formatted);
        }
    }

    public void debug(Supplier<String> supplier) {
        if (getEcPluginData().isDebugEnabled()){
            String formatted = "[Debug] " + supplier.get();
            plugin.getLogger().info(formatted);
        }
    }

    public void info(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        plugin.getLogger().info(formatted);
    }

    public void info(Supplier<String> supplier) {
        plugin.getLogger().info(supplier);
    }

    public void warning(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        plugin.getLogger().warning(formatted);
    }

    public void warning(Supplier<String> supplier) {
        plugin.getLogger().warning(supplier);
    }

    public void severe(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        plugin.getLogger().severe(formatted);
    }

    public void severe(Supplier<String> supplier) {
        plugin.getLogger().severe(supplier);
    }

}
