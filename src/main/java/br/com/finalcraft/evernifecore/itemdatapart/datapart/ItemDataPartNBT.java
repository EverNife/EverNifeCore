package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDataPartNBT extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        ItemStack override = NMSUtils.get().validateItemStackHandle(item);
        NMSUtils.get().applyNBTFromString(override, argument.trim());
        return override;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return true; //To expensive to check
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        NBTItem nbtItem = FCNBTUtil.getFrom(i.clone());//Clone it because we may need to remove the "display" tag
        if (nbtItem.hasNBTData()){
            String nbt = nbtItem.toString();
            if (!"{}".equals(nbt)){
                nbtItem.removeKey("display");//Remove LORE and DisplayName
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
