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

    public static Double parseDouble(String input, Double def){
        try {
            return Double.parseDouble(input);
        }catch (NumberFormatException e){
            return def;
        }
    }

    public static Double parseDouble(String input){
        return parseDouble(input, null);
    }

    public static Material parseMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            if (MCVersion.isHigherEquals(MCVersion.v1_12)){
                material = Material.matchMaterial(materialName, true);
            }
        }
        return material;
    }

}
