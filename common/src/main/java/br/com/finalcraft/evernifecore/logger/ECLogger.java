package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;

import java.util.function.Supplier;
import java.util.logging.Level;

public class ECLogger<DL extends IDebugModule> {

    private final ECPluginData plugin;
    private final ILogAdapter logAdapter;

    public ECLogger(ECPluginData plugin) {
        this.plugin = plugin;
        this.logAdapter = EverNifeCore.getPlatform().createLogAdapterFor(plugin);
    }

    public ECLogger(ECPluginData ecPluginData, DL[] debugModules) {
        this.plugin = ecPluginData;
        this.logAdapter = EverNifeCore.getPlatform().createLogAdapterFor(ecPluginData);
        this.plugin.defineDebugModules(debugModules);
    }

    public ECPluginData getEcPluginData() {
        return plugin;
    }

    public void log(Level level, String msg) {
        logAdapter.log(level, msg);
    }

    public void log(Level level, Supplier<String> supplier) {
        logAdapter.log(level, supplier.get());
    }

    public void debug(String message, Object... params) {
        if (getEcPluginData().isDebugEnabled()){
            String formatted = "[Debug] " + (params.length == 0 ? message : String.format(message, params));
            logAdapter.info(formatted);
        }
    }

    public void debug(Supplier<String> supplier) {
        if (getEcPluginData().isDebugEnabled()){
            String formatted = "[Debug] " + supplier.get();
            logAdapter.info(formatted);
        }
    }

    public void info(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        logAdapter.info(formatted);
    }

    public void info(Supplier<String> supplier) {
        logAdapter.info(supplier.get());
    }

    public void warning(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        logAdapter.warning(formatted);
    }

    public void warning(Supplier<String> supplier) {
        logAdapter.warning(supplier.get());
    }

    public void severe(String message, Object... params) {
        String formatted = params.length == 0 ? message : String.format(message, params);
        logAdapter.severe(formatted);
    }

    public void severe(Supplier<String> supplier) {
        logAdapter.severe(supplier.get());
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
            logAdapter.info(formatted);
        }
    }

    public void debugModule(DL debugModule, Supplier<String> supplier) {
        if (getEcPluginData().isDebugEnabled(debugModule)){
            String formatted = "[Debug (" + debugModule.getName()  + ") ] " + supplier.get();
            logAdapter.info(formatted);
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
