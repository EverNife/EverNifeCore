package br.com.finalcraft.evernifecore.hytale.argumento;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

public class HytaleArgumento extends Argumento {

    public HytaleArgumento(String argumento) {
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

