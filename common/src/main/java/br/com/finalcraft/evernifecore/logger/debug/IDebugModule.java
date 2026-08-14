package br.com.finalcraft.evernifecore.logger.debug;

import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.ECLogLevel;
import br.com.finalcraft.evernifecore.logger.ECLogger;

import java.util.function.Supplier;

/**
 * One switch in a plugin's {@code DebugMode.DebugModules} config block, and the door through which
 * everything behind that switch logs. Implemented by an enum, one constant per switch:
 *
 * <pre>
 * public enum MyDebug implements IDebugModule {
 *     ARENA("Logs arena state machine transitions.", true),
 *     ;
 *     //comment + enabledByDefault fields, and enabled starting from enabledByDefault
 *     public ECPluginData getPluginData() { return MyPlugin.instance.getPluginData(); }
 *     public boolean isEnabled()          { return enabled; }
 *     public void setEnabled(boolean e)   { this.enabled = e; }
 * }
 *
 * MyDebug.ARENA.debug("Arena {} moved to {}", arena.getId(), newState);
 * </pre>
 *
 * <p>{@link #debug} is the only verb the switch gates. {@code info}/{@code warning}/{@code severe}
 * are tagging: they name the module in the line and always log, because a warning does not become
 * less true when the operator is not debugging.</p>
 */
public interface IDebugModule {

    /** {@code MY_MODULE} for an enum constant - which is what every implementation so far is. */
    default String getName() {
        return this instanceof Enum ? ((Enum<?>) this).name() : getClass().getSimpleName();
    }

    /** The line written above this module's key in the config, explaining what it turns on. */
    default String getComment() {
        return null;
    }

    default boolean isEnabledByDefault() {
        return true;
    }

    /**
     * Platform gate. A module absent from the running platform is never seeded into the config and is
     * forced off when the block is read, so it can neither be switched on nor log.
     */
    default boolean isAvailable(IPlatform platform) {
        return true;
    }

    boolean isEnabled();

    void setEnabled(boolean enabled);

    default boolean onConfigLoad(ConfigSection section) {
        return section.getOrSetValueIfAbsent("DebugModules." + getName(), isEnabledByDefault(), getComment());
    }

    ECPluginData getPluginData();

    default ECLogger getLog() {
        return getPluginData().getLog();
    }

    default void debug(String message, Object... params) {
        //isDebugEnabled(this) already answered for the plugin AND for this module, so the line goes out
        //through the ungated outlet - ECLogger.debug would ask the plugin half of that question again
        if (getPluginData().isDebugEnabled(this)) {
            getLog().log(ECLogLevel.DEBUG, "[Debug (" + getName() + ")] " + message, params);
        }
    }

    /** @see ECLogger#debug(Supplier) */
    default void debug(Supplier<String> supplier) {
        if (getPluginData().isDebugEnabled(this)) {
            getLog().log(ECLogLevel.DEBUG, "[Debug (" + getName() + ")] " + supplier.get());
        }
    }

    default void info(String message, Object... params) {
        getLog().info("[" + getName() + "] " + message, params);
    }

    default void warning(String message, Object... params) {
        getLog().warning("[" + getName() + "] " + message, params);
    }

    default void severe(String message, Object... params) {
        getLog().severe("[" + getName() + "] " + message, params);
    }

}
