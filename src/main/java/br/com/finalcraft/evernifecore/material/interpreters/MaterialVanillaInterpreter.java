package br.com.finalcraft.evernifecore.material.interpreters;

import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class MaterialVanillaInterpreter {

    private Map<Material, Boolean> isVanilla = new HashMap();

    public MaterialVanillaInterpreter() {
        this.isVanilla.put(Material.AIR, true);
    }

    public boolean isVanilla(Material material) {
        Boolean vanilla = isVanilla.get(material);
        if (vanilla != null){
            return vanilla;
        }

        try {
            ItemStack itemStack = new ItemStack(material);
            String identifier = FCItemUtils.getMinecraftIdentifier(itemStack, false);
            if (identifier.startsWith("minecraft:")){
                vanilla = true;
            }else {
                vanilla = false;
            }
        }catch (Exception ignored){
            vanilla = false;
        }

        isVanilla.put(material, vanilla);
        return vanilla;
    }

}
