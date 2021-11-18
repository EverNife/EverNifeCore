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

    public static int getMaxFitAmount(ItemStack stack, Inventory inv) {
        ItemStack[] contents = inv.getStorageContents(); //Ignore Armor and Shield slots if PlayerInventory

        int result = 0;

        for (ItemStack contentStack : contents) {
            if (contentStack == null || contentStack.getType() == Material.AIR) {
                result += stack.getMaxStackSize();
            } else if (stack.isSimilar(contentStack)) {
                result += Math.max(stack.getMaxStackSize() - contentStack.getAmount(), 0);
            }
        }

        return result;
    }

}
