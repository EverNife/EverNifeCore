package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;

import java.util.UUID;

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

    private static MethodInvoker<Material> getMaterialByNumericID = null; static {
        try {
            getMaterialByNumericID = FCReflectionUtil.getMethod(Material.class, "getMaterial", int.class);
        }catch (Throwable ignored){

        }
    }

    public static Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isEmpty()){
            return null;
        }

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            if (MCVersion.isHigherEquals(MCVersion.v1_13)){
                material = Material.matchMaterial(materialName, true);
            }else if (getMaterialByNumericID != null && Character.isDigit(materialName.charAt(0))) {
                //On legacy, check if it's a numeric Material ID
                Integer numericID = FCInputReader.parseInt(materialName);
                if (numericID != null && numericID >= 0){
                    material = getMaterialByNumericID.invoke(null, numericID);
                }
            }
        }
        return material;
    }

    public static UUID parseUUID(String uuid){
        if (uuid == null || uuid.isEmpty() || uuid.length() != 36) return null;
        try {
            return UUID.fromString(uuid);
        }catch (Exception ignored){
            return null;
        }
    }

}
