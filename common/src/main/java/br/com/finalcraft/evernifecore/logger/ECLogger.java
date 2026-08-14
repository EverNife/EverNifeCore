package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.function.Supplier;

/**
 * One plugin's logger. Messages carry {@code {}} placeholders and are formatted by
 * {@link ECLogFormat}, which never throws and appends the stack trace of a trailing
 * {@link Throwable} at any level.
 *
 * <pre>
 * log.info("Loaded {} arenas for {}", count, world);
 * log.warning("Could not parse {}", file.getName(), failure);   //message + stack trace
 * </pre>
 */
public class ECLogger {

    private final ECPluginData plugin;
    private final ILogAdapter logAdapter;

    public ECLogger(ECPluginData plugin) {
        this.plugin = plugin;
        this.logAdapter = EverNifeCore.getPlatform().createLogAdapterFor(plugin);
    }

    public ECPluginData getEcPluginData() {
        return plugin;
    }

    public void info(String message, Object... params) {
        log(ECLogLevel.INFO, message, params);
    }

    public void warning(String message, Object... params) {
        log(ECLogLevel.WARNING, message, params);
    }

    public void severe(String message, Object... params) {
        log(ECLogLevel.SEVERE, message, params);
    }

    public void debug(String message, Object... params) {
        if (getEcPluginData().isDebugEnabled()) {
            log(ECLogLevel.DEBUG, "[Debug] " + message, params);
        }
    }

    /**
     * Debug whose message costs something to build. The supplier runs only once the switch is on -
     * which is why the other verbs have no supplier form: there, {@code get()} would always run.
     */
    public void debug(Supplier<String> supplier) {
        if (getEcPluginData().isDebugEnabled()) {
            log(ECLogLevel.DEBUG, "[Debug] " + supplier.get());
        }
    }

    /**
     * Secondary outlet, below the four verbs: for a caller holding the level in a variable rather
     * than naming it. {@code log(DEBUG, ...)} is the raw channel and is NOT gated by
     * {@code DebugMode} - {@link #debug(String, Object...)} is the one that asks.
     */
    public void log(ECLogLevel level, String message, Object... params) {
        logAdapter.log(level, ECLogFormat.format(message, params));
    }

}
