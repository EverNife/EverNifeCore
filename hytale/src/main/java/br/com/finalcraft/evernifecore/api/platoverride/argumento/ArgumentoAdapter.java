package br.com.finalcraft.evernifecore.api.platoverride.argumento;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

public class ArgumentoAdapter extends Argumento {

    public ArgumentoAdapter(String argumento) {
        super(argumento);
    }

    public JavaPlugin getPlugin(){
        return (JavaPlugin) PluginManager.get().getPlugins().stream()
                .filter(pluginBase -> pluginBase.getIdentifier().toString().equalsIgnoreCase(argumento))
                .findFirst()
                .orElse(null);
    }

    public World getWorld(){
        return argumento.isEmpty() ? null : Universe.get().getWorld(argumento);
    }

}
