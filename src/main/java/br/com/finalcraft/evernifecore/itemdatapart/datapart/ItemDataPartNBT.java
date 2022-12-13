package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemDataPartNBT extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        ItemMeta itemMeta = item.getItemMeta();
        NBTItem nbtItem = FCNBTUtil.getFrom(item);
        nbtItem.clearNBT();
        nbtItem.mergeCompound(FCNBTUtil.getFrom(argument.trim()));
        item.setItemMeta(itemMeta);
        return item;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return true; //To expensive to check
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        NBTItem nbtItem = FCNBTUtil.getFrom(i.clone());//Clone it because we may need to remove the "display" tag
        if (nbtItem.hasNBTData()){
            nbtItem.removeKey("display");//Remove LORE and DisplayName
            if (!MCVersion.isLowerEquals1_12()){
                nbtItem.removeKey("Damage");//Remove Damage
            }
            String nbt = nbtItem.toString();
            if (!"{}".equals(nbt)){
                output.add("nbt: " + nbt);
            }
        }
        return output;
    }

    @Override
    public int getPriority() {
        return 5; // AFTER "PRIORITY_MOST_EARLY"
    }

    @Override
    public boolean removeSpaces() {
        return false;
    }

    @Override
    public String[] createNames() {
        return new String[]{"nbt", "rawnbt"};
    }

}
