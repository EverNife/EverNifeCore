package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;


public class ItemDataPartLore extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        ItemMeta meta = item.getItemMeta();

        String argumentTransformed = ChatColor.translateAlternateColorCodes('&',argument);
        String[] parts = argumentTransformed.split("[#\\n]");
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        for (String part : parts) {
            lore.add(part);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        ItemMeta ms = base_item.getItemMeta();
        ItemMeta mp = other_item.getItemMeta();
        if (ms.hasLore()) {
            if (!mp.hasLore()) {
                return false;
            }

            if (ms.getLore().size() > mp.getLore().size()) {
                return false;
            }
            for (int i = 0; i < ms.getLore().size(); i++) {
                String base_item_lore_line = ms.getLore().get(i);
                if (!mp.getLore().get(i).equals(base_item_lore_line)) {
                    return false;
                }
            }

        }
        return true;
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        if (itemStack.getItemMeta().hasLore()) {
            for (String line : itemStack.getItemMeta().getLore()) {
                String split[] = line.split("\\R", -1);
                for (String splitedLine : split) {
                    if (FCColorUtil.stripColor(splitedLine).isEmpty()){//Without colors, this line is empty!
                        output.add("lore:");
                    }else {
                        output.add("lore:" + splitedLine.replaceAll(String.valueOf(ChatColor.COLOR_CHAR), "&"));
                    }
                }
            }
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
        return new String[]{"lore", "description"};
    }


}
