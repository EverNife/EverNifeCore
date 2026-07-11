package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.interpreters.MaterialHeadInterpreter;
import br.com.finalcraft.evernifecore.minecraft.interpreters.MaterialSignInterpreter;
import br.com.finalcraft.evernifecore.minecraft.interpreters.MaterialVanillaInterpreter;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import br.com.finalcraft.everylibs.util.FCInputReader;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import org.bukkit.Material;

//We use separated interpreters on this class to prevent static instantion of non-used classes
public class FCMaterialUtil {

    /**
     * Check if a Material is Vanilla or Forge!
     */
    private static MaterialVanillaInterpreter vanillaInterpreter;
    public static boolean isVanilla(Material material) {
        if (vanillaInterpreter == null){
            vanillaInterpreter = new MaterialVanillaInterpreter();
        }
        return vanillaInterpreter.isVanilla(material);
    }

    /**
     * Check if a Material is a Head!
     */
    private static MaterialHeadInterpreter materialHeadInterpreter;
    public static boolean isHead(Material material) {
        if (materialHeadInterpreter == null){
            materialHeadInterpreter = new MaterialHeadInterpreter();
        }
        return materialHeadInterpreter.isHead(material);
    }

    /**
     * Check if a Material is a Sign!
     */
    private static MaterialSignInterpreter materialSignInterpreter;
    public static boolean isSign(Material material) {
        if (materialSignInterpreter == null){
            materialSignInterpreter = new MaterialSignInterpreter();
        }
        return materialSignInterpreter.isSign(material);
    }

    private static MethodInvoker<Material> getMaterialByNumericID = null; static {
        try {
            getMaterialByNumericID = FCReflectionUtil.getMethods().getMethod(Material.class, "getMaterial", int.class);
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

}
