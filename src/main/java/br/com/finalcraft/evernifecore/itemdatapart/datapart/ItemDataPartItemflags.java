package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemDataPartItemflags extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        ItemMeta meta = item.getItemMeta();

        if (argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("all")) {
            meta.addItemFlags(ItemFlag.values());
        } else {
            String[] flags = argument.split("#");

            for (String flag : flags) {
                flag = flag.toUpperCase().replaceAll(" ", "_");
                if (!flag.startsWith("HIDE_")) {
                    flag = "HIDE_" + flag;
                }
                try {
                    ItemFlag itemflag = ItemFlag.valueOf(flag.toUpperCase());
                    meta.addItemFlags(itemflag);
                } catch (Exception e) {
                    EverNifeCore.warning("Mistake in Config: '" + flag + "' is not a valid '" + used_name + "'.");
                }
            }

        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return true; //Does not matter
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        ItemMeta meta = i.getItemMeta();
        if (meta.getItemFlags() != null) {
            for (ItemFlag flag : meta.getItemFlags()) {
                output.add("itemflag:" + flag.name());
            }
        }
        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_LATE;
    }

    @Override
    public boolean removeSpaces() {
        return false;
    }

    @Override
    public String[] createNames() {
        return new String[]{"itemflag", "hideflag", "flag", "itemflags", "hideflags", "flags"};
    }

}
