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

    public static Material parseMaterial(String materialName) {
        if (MCVersion.isLegacy()){
            return Material.getMaterial(materialName);
        }
        Material material = Material.matchMaterial(materialName, false);
        if (material == null) {
            material = Material.matchMaterial(materialName, true);
        }
        return material;
    }

}
