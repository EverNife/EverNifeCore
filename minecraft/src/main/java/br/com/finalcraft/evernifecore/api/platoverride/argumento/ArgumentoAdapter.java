package br.com.finalcraft.evernifecore.api.platoverride.argumento;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public class ArgumentoAdapter extends Argumento {

    public ArgumentoAdapter(String argumento) {
        super(argumento);
    }

    public Plugin getPlugin(){
        return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(pluginBase -> pluginBase.getName().equalsIgnoreCase(argumento))
                .findFirst()
                .orElse(null);
    }

    public World getWorld(){
        return argumento.isEmpty() ? null : Bukkit.getWorld(argumento);
    }

}

