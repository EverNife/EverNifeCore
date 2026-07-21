package br.com.finalcraft.evernifecore.minecraft.ecplugin;

import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional Bukkit base class for ECPlugins: bridges the Bukkit lifecycle into the
 * {@link IECPluginBootstrap} hooks, so the shared wiring can live in the plugin's
 * platform-agnostic module and the main class only carries the platform extras.
 *
 * <p>A plugin that cannot change its base class can implement {@link IECPluginBootstrap}
 * directly and make these same one-line bridge calls from its own lifecycle methods.</p>
 */
public abstract class ECBukkitPlugin extends JavaPlugin implements IECPluginBootstrap {

    @Override
    public void onEnable() {
        runECPluginEnable();
    }

    @Override
    public void onDisable() {
        //listeners are unregistered first by the default onECPluginShutdownPre()
        runECPluginShutdown();
    }

}
