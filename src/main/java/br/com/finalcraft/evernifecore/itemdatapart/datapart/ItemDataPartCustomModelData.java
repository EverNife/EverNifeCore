package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemDataPartCustomModelData extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        int custommodeldata = FCInputReader.parseInt(argument, -1);
        if (custommodeldata == -1) {
            EverNifeCore.warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. It needs to be a number like '1', '12' or '64'.");
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(custommodeldata);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        if (base_item.hasItemMeta() && other_item.hasItemMeta()) {
            return base_item.getItemMeta().getCustomModelData() == other_item.getItemMeta().getCustomModelData();
        }
        return true;
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        if (itemStack.hasItemMeta()) {
            int d = itemStack.getItemMeta().getCustomModelData();
            if (d != -1) {
                output.add("CustomModelData:" + d);
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
        return new String[]{"CustomModelData"};
    }

    @Override
    public MCDetailedVersion getMinimumVersion() {
        return MCDetailedVersion.v1_14_R1;
    }
}
