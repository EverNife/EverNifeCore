package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.function.Supplier;
import java.util.logging.Level;

public class ECLogger {

    private final ECPluginData ecPluginData;

    public ECLogger(ECPluginData ecPluginData) {
        this.ecPluginData = ecPluginData;
    }

    public void log(Level level, String msg) {
        ecPluginData.getPlugin().getLogger().log(level, msg);
    }

    public void log(Level level, Supplier<String> supplier) {
        ecPluginData.getPlugin().getLogger().log(level, supplier);
    }

    public void debug(String message, Object... params) {
        if (ecPluginData.isDebugEnabled()){
            String formatted = "[Debug]" + (params.length == 0 ? message : String.format(message, params));
            ecPluginData.getPlugin().getLogger().info(formatted);
        }
    }

    public void debug(Supplier<String> supplier) {
        if (ecPluginData.isDebugEnabled()){
            String formatted = "[Debug]" + supplier.get();
            ecPluginData.getPlugin().getLogger().info(formatted);
        }
    }

    public void info(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        ecPluginData.getPlugin().getLogger().info(formatted);
    }

    public void info(Supplier<String> supplier) {
        ecPluginData.getPlugin().getLogger().info(supplier);
    }

    public void warning(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        ecPluginData.getPlugin().getLogger().warning(formatted);
    }

    public void warning(Supplier<String> supplier) {
        ecPluginData.getPlugin().getLogger().warning(supplier);
    }

    public void severe(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        ecPluginData.getPlugin().getLogger().severe(formatted);
    }

    public void severe(Supplier<String> supplier) {
        ecPluginData.getPlugin().getLogger().severe(supplier);
    }

}
