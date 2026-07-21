package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ECLogger;

/**
 * The platform-agnostic bootstrap contract of an ECPlugin.
 *
 * <p>It is implemented by the plugin's platform entry point, usually through the platform base
 * classes (ECBukkitPlugin on Bukkit, ECHytalePlugin on Hytale) that bridge the platform lifecycle
 * into these hooks. The intended layering:</p>
 *
 * <ul>
 *   <li><b>plugin common module:</b> ONE interface extending this one, implementing the mandatory
 *       {@link #onECPluginEnable()} / {@link #onECPluginShutdown()} / {@link #onECPluginReload()}
 *       with the wiring every platform shares;</li>
 *   <li><b>each platform main class:</b> implements that interface on top of its platform base
 *       class and adds its platform-specific extras through {@link #onECPluginEnableLate()} (or
 *       {@link #onECPluginEnableEarly()}).</li>
 * </ul>
 *
 * <p>Enable runs {@link #onECPluginEnableEarly()} -&gt; {@link #onECPluginEnable()} -&gt;
 * {@link #onECPluginEnableLate()}; shutdown runs {@link #onECPluginShutdownEarly()} -&gt;
 * {@link #onECPluginShutdown()} -&gt; {@link #onECPluginShutdownLate()}. The Early/Late hooks are
 * optional (no-op by default), except {@link #onECPluginShutdownLate()}, whose default unregisters
 * every listener the plugin registered.</p>
 */
public interface IECPluginBootstrap {

    /** The {@link ECPluginData} of this plugin (created on first access). */
    public default ECPluginData getPluginData(){
        return ECPluginManager.getOrCreateECorePluginData(this);
    }

    /** This plugin's logger - shorthand for {@code getPluginData().getLog()}. */
    public default ECLogger<?> getLog(){
        return getPluginData().getLog();
    }

    // ------------------------------------------------------------------
    //  Enable
    // ------------------------------------------------------------------

    /** Optional enable extras that must run BEFORE the shared wiring. No-op by default. */
    public default void onECPluginEnableEarly() {

    }

    /** The platform-agnostic enable wiring. Mandatory - implemented once in the plugin's common bootstrap. */
    public void onECPluginEnable();

    /**
     * Optional enable extras that run AFTER the shared wiring - the usual home for a platform main
     * class's platform-specific registrations (commands, listeners, integrations). No-op by default.
     */
    public default void onECPluginEnableLate() {

    }

    // ------------------------------------------------------------------
    //  Shutdown
    // ------------------------------------------------------------------

    /** Optional shutdown extras that must run BEFORE the shared teardown. No-op by default. */
    public default void onECPluginShutdownEarly() {

    }

    /** The platform-agnostic shutdown teardown. Mandatory - implemented once in the plugin's common bootstrap. */
    public void onECPluginShutdown();

    /**
     * Optional shutdown extras that run AFTER the shared teardown. The default unregisters every
     * listener this plugin registered through {@link ECListener#register}; a class that overrides
     * this replaces that cleanup, so it should call {@code ECListener.unregisterAll(getPluginData())}
     * (or {@code IECPluginBootstrap.super.onECPluginShutdownLate()}) itself if it still wants it.
     */
    public default void onECPluginShutdownLate() {
        ECListener.unregisterAll(getPluginData());
    }

    // ------------------------------------------------------------------
    //  Reload
    // ------------------------------------------------------------------

    /**
     * The reload logic. Mandatory: a bootstrap plugin is reloadable through this hook (unless it
     * declares an {@code @ECPlugin.Reload} method, which wins and lets it also set {@code reloadAfter}).
     */
    public void onECPluginReload();

    // ------------------------------------------------------------------
    //  First tick
    // ------------------------------------------------------------------

    /**
     * Returns a task to run SYNCHRONOUSLY on the server's first tick - after every plugin has
     * finished enabling - or {@code null} (the default) to run nothing. It is handed to
     * {@link br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform#runOnFirstTick(Runnable)};
     * a platform without a first-tick hook (Hytale) simply runs it in place.
     */
    public default Runnable runOnFirstTick() {
        return null;
    }

    // ------------------------------------------------------------------
    //  Orchestration (called by the platform bridge)
    // ------------------------------------------------------------------

    /**
     * The enable entry point invoked by the platform bridge: the standard start banner, then
     * Early -&gt; main -&gt; Late wiring, the enabled banner, and finally the first-tick task (if any).
     * Override only when a plugin needs a different orchestration.
     */
    public default void runECPluginEnable(){
        IPluginMetaInfo metaInfo = getPluginData().getMetaInfo();
        getLog().info("§aStarting " + metaInfo.getName() + " v" + metaInfo.getVersion());

        onECPluginEnableEarly();
        onECPluginEnable();
        onECPluginEnableLate();

        getLog().info("§a" + metaInfo.getName() + " successfully enabled!");

        Runnable firstTick = runOnFirstTick();
        if (firstTick != null) {
            EverNifeCore.getPlatform().runOnFirstTick(firstTick);
        }
    }

    /**
     * The shutdown entry point invoked by the platform bridge: Early -&gt; main -&gt; Late teardown
     * (the Late default unregisters this plugin's listeners).
     */
    public default void runECPluginShutdown(){
        onECPluginShutdownEarly();
        onECPluginShutdown();
        onECPluginShutdownLate();
    }

}
