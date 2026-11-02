package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.evernifecore.time.ECScheduler;

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
 * every listener AND every command the plugin registered - so the plugin's entry points are gone
 * before {@link #onECPluginShutdown()} tears down the resources those listeners/commands could touch.</p>
 *
 * <p>The running instance is published for the plugin: an {@link ECBootstrap} declared in the common
 * bootstrap is filled from {@link #runECPluginInstantiate()} and every {@link #runECPluginEnable()},
 * and emptied when {@link #runECPluginShutdown()} has finished - the plugin writes no bookkeeping of
 * its own.</p>
 */
public interface IECPluginBootstrap {

    /** The {@link ECPluginData} of this plugin (created on first access). */
    public default ECPluginData getPluginData(){
        return ECPluginManager.getOrCreateECorePluginData(this);
    }

    /**
     * The platform object this bootstrap runs as - a Bukkit {@code JavaPlugin}, a Hytale one - for a
     * caller that can name it. {@code common} cannot, and gets it as {@link Object}.
     */
    public default Object getPlugin(){
        return getPluginData().getPlugin();
    }

    /**
     * Framework-internal bookkeeping: drops whatever this plugin kept of its own {@link ECPluginData},
     * so the next {@link #getPluginData()} asks for it again. Called by
     * {@link ECPluginManager#removePluginData(String)} when that data leaves the registry - plugin
     * authors never call this directly. The default keeps nothing and so has nothing to drop; the
     * platform base classes, which answer {@link #getPluginData()} from a field, override it.
     */
    public default void clearCachedPluginData(){

    }

    /** This plugin's logger - shorthand for {@code getPluginData().getLog()}. */
    public default ECLogger getLog(){
        return getPluginData().getLog();
    }

    // ------------------------------------------------------------------
    //  Enable
    // ------------------------------------------------------------------

    /**
     * Optional extras that must run the moment the platform builds this plugin, before any lifecycle
     * hook. The instance is already published when it runs. No-op by default.
     */
    public default void onInstantiate() {

    }

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
     * Optional shutdown extras that run BEFORE the shared teardown. The default takes back everything the core keeps
     * for this plugin, writing its regions, groups and quests while its storage is still open: managers, registrations,
     * listeners, commands, argument parsers and bus subscriptions - so nothing still reaches the plugin when
     * {@link #onECPluginShutdown()} closes the resources those touch. It is the same set
     * {@link ECPluginManager#removePluginData(String)} takes back for a plugin that leaves without this hook; each
     * step runs even when an earlier one threw, and the failure goes to the plugin's log.
     *
     * <p>An override REPLACES that cleanup, so it has to ask for it back: {@code super.onECPluginShutdownPre()} from
     * a subclass of a base that already implements this interface ({@code ECBukkitPlugin}, {@code ECHytalePlugin}),
     * or {@code IECPluginBootstrap.super.onECPluginShutdownPre()} only from a class that implements it directly - the
     * qualified form does not compile anywhere else.</p>
     */
    public default void onECPluginShutdownPre() {
        ECPluginManager.releaseEverythingOf(getPluginData(), true);
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
     * Returns a task handed to {@link ECScheduler#onMainThreadLater} once enable
     * finishes, or {@code null} (the default) to run nothing. It runs once every plugin has enabled:
     * on Bukkit on the first tick, on the server main thread; on Hytale once all plugins have set up
     * and all worlds are loaded. A plugin re-enabled at runtime gets it after this enable returned.
     * See {@link ECScheduler#onMainThreadLater} for the exact contract.
     */
    public default Runnable runOnFirstTick() {
        return null;
    }

    // ------------------------------------------------------------------
    //  Orchestration (called by the platform bridge)
    // ------------------------------------------------------------------

    /**
     * The instantiate entry point invoked by the platform bridge from its constructor: publishes this
     * instance, then runs {@link #onInstantiate()}. A plugin implementing this interface directly makes
     * this same call from its own constructor.
     */
    public default void runECPluginInstantiate(){
        ECBootstrap.publish(this);
        onInstantiate();
    }

    /**
     * The enable entry point invoked by the platform bridge: this plugin's debug switches are read
     * first, then the Pre -&gt; main -&gt; Post wiring, the enabled banner, and finally the first-tick
     * task (if any). Override only when a plugin needs a different orchestration.
     */
    public default void runECPluginEnable(){
        //an enable can also follow a shutdown, when a plugin manager disables and re-enables this same
        //object; the constructor does not run a second time, so only this can publish it back
        ECBootstrap.publish(this);

        ECPluginData pluginData = getPluginData();
        IPluginMetaInfo metaInfo = pluginData.getMetaInfo();

        //before the first hook, so the whole enable already logs under the switches the file asks for
        pluginData.loadDebugConfig();

        onECPluginEnablePre();
        onECPluginEnable();
        onECPluginEnablePost();

        getLog().info("§a" + metaInfo.getName() + " successfully enabled!");

        Runnable firstTick = runOnFirstTick();
        if (firstTick != null) {
            //fire-and-forget: onMainThreadLater already prints any failure of the task
            ECScheduler.onMainThreadLater(firstTick);
        }
    }

    /**
     * The shutdown entry point invoked by the platform bridge: Pre -&gt; main -&gt; Post teardown
     * (the Pre default unregisters this plugin's listeners before the main teardown closes resources),
     * then the file loggers this plugin opened and left open, and finally this instance itself - which
     * is why a plugin's own teardown can still reach it.
     */
    public default void runECPluginShutdown(){
        onECPluginShutdownPre();
        onECPluginShutdown();
        onECPluginShutdownPost();

        //after the last hook: a plugin writes its closing lines during its own teardown
        getPluginData().closeOpenFileLoggers();

        //last of all, so the plugin's own teardown can still reach itself through its accessor
        ECBootstrap.unpublish(this);
    }

}
