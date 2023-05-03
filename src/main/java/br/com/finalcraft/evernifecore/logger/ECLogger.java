package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;
import java.util.logging.Level;

public class ECLogger<DL extends IDebugModule> {

    private final JavaPlugin plugin;
    private transient ECPluginData ecPluginData = null;

    public ECLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ECLogger(JavaPlugin plugin, DL[] debugModules) {
        this.plugin = plugin;
        getEcPluginData().defineDebugModules(debugModules);
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

    // -----------------------------------------------------------------------------------------------------------------
    //  Modulirized Logging
    // -----------------------------------------------------------------------------------------------------------------

    public void logModule(DL debugModule, Level level, String msg) {
        this.log(level, "[" + debugModule.getName() + "] " + msg);
    }

    public void logModule(DL debugModule, Level level, Supplier<String> supplier) {
        this.log(level, () -> "[" + debugModule.getName() + "] " + supplier.get());
    }

    public void debugModule(DL debugModule, String message, Object... params) {
        if (getEcPluginData().isDebugEnabled(debugModule)){
            String formatted = "[Debug (" + debugModule.getName()  + ") ] " + (params.length == 0 ? message : String.format(message, params));
            plugin.getLogger().info(formatted);
        }
    }

    public void debugModule(DL debugModule, Supplier<String> supplier) {
        if (getEcPluginData().isDebugEnabled(debugModule)){
            String formatted = "[Debug (" + debugModule.getName()  + ") ] " + supplier.get();
            plugin.getLogger().info(formatted);
        }
    }

    public void infoModule(DL debugModule, String message, Object... params) {
        this.info("[" + debugModule.getName() + "] " + message, params);
    }

    public void infoModule(DL debugModule, Supplier<String> supplier) {
        this.info(() -> "[" + debugModule.getName() + "] " + supplier.get());
    }

    public void warningModule(DL debugModule, String message, Object... params) {
        this.warning("[" + debugModule.getName() + "] " + message, params);
    }

    public void warningModule(DL debugModule, Supplier<String> supplier) {
        this.warning(() -> "[" + debugModule.getName() + "] " + supplier.get());
    }

    public void severeModule(DL debugModule, String message, Object... params) {
        this.severe("[" + debugModule.getName() + "] " + message, params);
    }

    public void severeModule(DL debugModule, Supplier<String> supplier) {
        this.severe(() -> "[" + debugModule.getName() + "] " + supplier.get());
    }

}
