package br.com.finalcraft.evernifecore.material.interpreters;

import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class MaterialVanillaInterpreter {

    private Map<Material, Boolean> isVanilla = new HashMap();
    public boolean isVanilla(Material material) {
        Boolean vanilla = isVanilla.get(material);
        if (vanilla != null){
            return vanilla;
        }

        try {
            ItemStack itemStack = FCItemFactory.from(material).build();
            String identifier = FCItemUtils.getMinecraftIdentifier(itemStack);
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
