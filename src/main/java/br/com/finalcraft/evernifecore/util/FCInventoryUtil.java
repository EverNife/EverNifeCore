package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.itemstack.ComparableItem;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class FCInventoryUtil {

    public static int getAmount(Player player, ComparableItem comparableItem){
        int amount = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() != Material.AIR){
                if (comparableItem.match(content)){
                    amount += content.getAmount();
                }
            }
        }
        return amount;
    }

    public static boolean contains(Player player, ComparableItem comparableItem, int amount){
        assert amount > 0 : "Amount must be > 0";

        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() != Material.AIR){
                if (comparableItem.match(content)){
                    amount -= content.getAmount();
                    if (amount <= 0){
                        break;
                    }
                }
            }
        }

        return amount <= 0;
    }

    public static boolean removeFrom(Player player, ComparableItem comparableItem, int amount){
        assert amount > 0 : "Amount must be > 0";

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack content = storage[i];

            if (content != null && content.getType() != Material.AIR){
                if (comparableItem.match(content)){
                    if (amount >= content.getAmount()){
                        storage[i] = null;
                        amount -= content.getAmount();
                    }else {
                        content.setAmount(content.getAmount() - amount);
                        amount = 0;
                    }
                    if (amount == 0){
                        break;
                    }
                }
            }
        }

        if (amount == 0){
            player.getInventory().setStorageContents(storage);
            return true;
        }

        return false;
    }

    public static int getMaxFitAmount(ComparableItem comparableItem, Inventory inv) {
        ItemStack[] contents =
                !MCVersion.isLowerEquals(MCVersion.v1_7_10) ? inv.getStorageContents()  //Ignore Armor and Shield slots if PlayerInventory
                        : inv.getContents(); // 1.7.10 does not have "getStorageContents()"

        int result = 0;

        for (ItemStack contentStack : contents) {
            if (contentStack == null || contentStack.getType() == Material.AIR) {
                result += comparableItem.getItemStack().getMaxStackSize();
            } else if (comparableItem.match(contentStack)) {
                result += Math.max(comparableItem.getItemStack().getMaxStackSize() - contentStack.getAmount(), 0);
            }
        }

        return result;
    }

    public static boolean canFit(ComparableItem comparableItem, Inventory inv){
        int amount = comparableItem.getItemStack().getAmount();
        return amount <= getMaxFitAmount(comparableItem, inv);
    }


    public static int getAmount(Player player, ItemStack itemStack){
        int amount = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() != Material.AIR){
                if (content.isSimilar(itemStack)){
                    amount += content.getAmount();
                }
            }
        }
        return amount;
    }

    public static boolean contains(Player player, ItemStack itemStack, int amount){
        assert amount > 0 : "Amount must be > 0";

        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() != Material.AIR){
                if (content.isSimilar(itemStack)){
                    amount -= content.getAmount();
                    if (amount <= 0){
                        break;
                    }
                }
            }
        }

        return amount <= 0;
    }

    public static boolean removeFrom(Player player, ItemStack itemStack, int amount){
        assert amount > 0 : "Amount must be > 0";

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack content = storage[i];

            if (content != null && content.getType() != Material.AIR){
                if (content.isSimilar(itemStack)){
                    if (amount >= content.getAmount()){
                        storage[i] = null;
                        amount -= content.getAmount();
                    }else {
                        content.setAmount(content.getAmount() - amount);
                        amount = 0;
                    }
                    if (amount == 0){
                        break;
                    }
                }
            }
        }

        if (amount == 0){
            player.getInventory().setStorageContents(storage);
            return true;
        }

        return false;
    }

    public static int getMaxFitAmount(ItemStack stack, Inventory inv) {
        ItemStack[] contents =
                !MCVersion.isLowerEquals(MCVersion.v1_7_10) ? inv.getStorageContents()  //Ignore Armor and Shield slots if PlayerInventory
                        : inv.getContents(); // 1.7.10 does not have "getStorageContents()"

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

    public static boolean canFit(ItemStack itemStack, Inventory inv){
        int amount = itemStack.getAmount();
        return amount <= getMaxFitAmount(itemStack, inv);
    }

}
