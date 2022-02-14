package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;

public class FCInputReader {

    public static Integer parseInt(String input, Integer def){
        try {
            return Integer.parseInt(input);
        }catch (NumberFormatException e){
            return def;
        }
    }

    public static Integer parseInt(String input){
        return parseInt(input, null);
    }

    public static Material parseMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            if (MCVersion.isCurrentEqualOrHigher(MCVersion.v1_12_R2)){
                material = Material.matchMaterial(materialName, true);
            }
        }
        return material;
    }

}
