package br.com.finalcraft.evernifecore.material;

import br.com.finalcraft.evernifecore.material.interpreters.MaterialHeadInterpreter;
import br.com.finalcraft.evernifecore.material.interpreters.MaterialSignInterpreter;
import br.com.finalcraft.evernifecore.material.interpreters.MaterialVanillaInterpreter;
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

}
