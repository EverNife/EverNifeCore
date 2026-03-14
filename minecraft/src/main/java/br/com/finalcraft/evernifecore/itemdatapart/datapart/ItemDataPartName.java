package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemDataPartName extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        return FCItemFactory.from(item)
                .displayName(argument)
                .build();
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        ItemMeta ms = base_item.getItemMeta();
        ItemMeta mp = other_item.getItemMeta();
        if (ms.hasDisplayName()) {
            if (!mp.hasDisplayName()) {
                return false;
            }
            return ms.getDisplayName().equals(mp.getDisplayName());
        }
        return true;
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        ItemMeta meta = i.getItemMeta();
        if (meta.hasDisplayName()) {
            output.add("name:" + meta.getDisplayName().replaceAll(String.valueOf(ChatColor.COLOR_CHAR), "&"));
        }
        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_NORMAL;
    }

    @Override
    public boolean removeSpaces() {
        return false;
    }

    @Override
    public String[] createNames() {
        return new String[]{"name", "text", "title"};
    }

}
