package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.everforgelib.api.customnpcs.EFLibAPI;
import br.com.finalcraft.everforgelib.api.customnpcs.IWorld;
import org.bukkit.World;

public class FCWorldUtil {

    public static IWorld getIWorld(World world){
        return EFLibAPI.getIWorld(world.getName());
    }

}
