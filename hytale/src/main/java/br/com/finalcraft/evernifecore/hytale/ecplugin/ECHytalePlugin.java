package br.com.finalcraft.evernifecore.hytale.ecplugin;

import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * Optional Hytale base class for ECPlugins: bridges the Hytale lifecycle (setup/shutdown) into the
 * {@link IECPluginBootstrap} hooks, so the shared wiring can live in the plugin's platform-agnostic
 * module and the main class only carries the platform extras.
 *
 * <p>A plugin that cannot change its base class can implement {@link IECPluginBootstrap}
 * directly and make these same one-line bridge calls from its own lifecycle methods.</p>
 */
public abstract class ECHytalePlugin extends JavaPlugin implements IECPluginBootstrap {

    protected ECHytalePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        runECPluginEnable();
    }

    @Override
    public void shutdown() {
        runECPluginShutdown();
    }

}
