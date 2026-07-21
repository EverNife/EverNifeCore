package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
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
 *       class and adds its platform-specific extras through {@link #onECPluginEnablePost()} (or
 *       {@link #onECPluginEnablePre()}).</li>
 * </ul>
 *
 * <p>Enable runs {@link #onECPluginEnablePre()} -&gt; {@link #onECPluginEnable()} -&gt;
 * {@link #onECPluginEnablePost()}; shutdown runs {@link #onECPluginShutdownPre()} -&gt;
 * {@link #onECPluginShutdown()} -&gt; {@link #onECPluginShutdownPost()}. The Pre/Post hooks are
 * optional (no-op by default), except {@link #onECPluginShutdownPre()}, whose default unregisters
 * every listener the plugin registered - so the plugin's entry points are gone before
 * {@link #onECPluginShutdown()} tears down the resources those listeners could touch.</p>
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
    public default void onECPluginEnablePre() {

    }

    /** The platform-agnostic enable wiring. Mandatory - implemented once in the plugin's common bootstrap. */
    public void onECPluginEnable();

    /**
     * Optional enable extras that run AFTER the shared wiring - the usual home for a platform main
     * class's platform-specific registrations (commands, listeners, integrations). No-op by default.
     */
    public default void onECPluginEnablePost() {

    }

    // ------------------------------------------------------------------
    //  Shutdown
    // ------------------------------------------------------------------

    /**
     * Optional shutdown extras that run BEFORE the shared teardown. The default unregisters every
     * listener this plugin registered through {@link ECListener#register}, so the plugin stops
     * receiving events before {@link #onECPluginShutdown()} closes the resources those events touch.
     * A class that overrides this replaces that cleanup, so it should call
     * {@code ECListener.unregisterAll(getPluginData())} (or
     * {@code IECPluginBootstrap.super.onECPluginShutdownPre()}) itself if it still wants it.
     */
    public default void onECPluginShutdownPre() {
        ECListener.unregisterAll(getPluginData());
    }

    /** The platform-agnostic shutdown teardown. Mandatory - implemented once in the plugin's common bootstrap. */
    public void onECPluginShutdown();

    /** Optional shutdown extras that run AFTER the shared teardown. No-op by default. */
    public default void onECPluginShutdownPost() {

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
     * Returns a task handed to {@link IPlatform#runOnFirstTick(Runnable)} once enable finishes, or
     * {@code null} (the default) to run nothing. The exact timing is platform-specific: on Bukkit it
     * runs on the server's first tick (after every plugin has enabled); on Hytale there is no such
     * global hook yet, so it currently runs in place at the end of enable. See
     * {@link IPlatform#runOnFirstTick(Runnable)} for the per-platform contract.
     */
    public default Runnable runOnFirstTick() {
        return null;
    }

    // ------------------------------------------------------------------
    //  Orchestration (called by the platform bridge)
    // ------------------------------------------------------------------

    /**
     * The enable entry point invoked by the platform bridge: the standard start banner, then
     * Pre -&gt; main -&gt; Post wiring, the enabled banner, and finally the first-tick task (if any).
     * Override only when a plugin needs a different orchestration.
     */
    public default void runECPluginEnable(){
        IPluginMetaInfo metaInfo = getPluginData().getMetaInfo();
        getLog().info("§aStarting " + metaInfo.getName() + " v" + metaInfo.getVersion());

        onECPluginEnablePre();
        onECPluginEnable();
        onECPluginEnablePost();

        getLog().info("§a" + metaInfo.getName() + " successfully enabled!");

        Runnable firstTick = runOnFirstTick();
        if (firstTick != null) {
            EverNifeCore.getPlatform().runOnFirstTick(firstTick);
        }
    }

    /**
     * The shutdown entry point invoked by the platform bridge: Pre -&gt; main -&gt; Post teardown
     * (the Pre default unregisters this plugin's listeners before the main teardown closes resources).
     */
    public default void runECPluginShutdown(){
        onECPluginShutdownPre();
        onECPluginShutdown();
        onECPluginShutdownPost();
    }

}
