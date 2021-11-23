package br.com.finalcraft.evernifecore.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FCInventoryUtil {

    public static boolean getAmount(Player player, ItemStack itemStack){
        int amount = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() != Material.AIR){
                if (content.isSimilar(itemStack)){
                    amount += content.getAmount();
                }
            }
        }
        return amount <= 0;
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


}
