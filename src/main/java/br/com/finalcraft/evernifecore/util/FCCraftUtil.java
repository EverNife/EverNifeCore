package br.com.finalcraft.evernifecore.util;

import org.bukkit.Material;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

//The code from this class is inspired on QuestWorld's "PlayerTools.class"
public class FCCraftUtil {

    public static int getMaxCraftAmount(CraftingInventory inv) {
        if (inv.getResult() == null){
            return 0;
        }

        int defaultItemAmount = inv.getResult().getAmount();
        int leastMaterialCount = Integer.MAX_VALUE;

        for (ItemStack ingredient : inv.getMatrix()) {
            if (ingredient != null && ingredient.getType() != Material.AIR && ingredient.getAmount() < leastMaterialCount) {
                leastMaterialCount = ingredient.getAmount();
            }
        }

        return defaultItemAmount * leastMaterialCount;
    }

}
