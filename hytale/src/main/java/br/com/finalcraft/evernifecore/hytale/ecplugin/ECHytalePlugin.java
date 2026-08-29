package br.com.finalcraft.evernifecore.hytale.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyPlatform;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * Optional Hytale base class for ECPlugins: bridges the Hytale lifecycle (setup/start/shutdown) into
 * the {@link IECPluginBootstrap} hooks, so the shared wiring can live in the plugin's platform-agnostic
 * module and the main class only carries the platform extras.
 *
 * <p>A plugin that cannot change its base class can implement {@link IECPluginBootstrap}
 * directly and make these same one-line bridge calls from its own lifecycle methods.</p>
 */
public abstract class ECHytalePlugin extends JavaPlugin implements IECPluginBootstrap {

    private volatile ECPluginData pluginData;

    {
        runECPluginInstantiate();
    }

    protected ECHytalePlugin(JavaPluginInit init) {
        super(init);
    }

    /** Resolved once and kept here, so a plugin logging from a hot path reads a field. */
    @Override
    public ECPluginData getPluginData() {
        ECPluginData cached = this.pluginData;
        if (cached == null) {
            cached = IECPluginBootstrap.super.getPluginData();
            this.pluginData = cached;
        }
        return cached;
    }

    @Override
    public void clearCachedPluginData() {
        this.pluginData = null;
    }

    @Override
    protected void setup() {
        runECPluginEnable();
    }

    @Override
    protected void start() {
        //The server reached the start phase: every plugin finished setup() and (per the Hytale
        //lifecycle) all worlds are loaded. Release the tasks IPlatform.runOnMainThreadNextTick buffered before
        //the start phase - the closest Hytale has to Bukkit's "first tick after all plugins enabled".
        //Idempotent across plugins; if worlds turn out to load after this, AllWorldsLoadedEvent is the
        //alternative trigger.
        if (EverNifeCore.getPlatform() instanceof HyPlatform hyPlatform) {
            hyPlatform.flushPendingMainThreadTasks();
        }
    }

    @Override
    public void shutdown() {
        runECPluginShutdown();
    }

}
