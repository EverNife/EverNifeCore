package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.List;

public class ItemDataPartItemId extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        return new ItemStack(
                argument,
                item.getQuantity(),
                item.getDurability(),
                item.getMaxDurability(),
                item.getMetadata()
        );
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getItemId().equals(other_item.getItemId());
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        output.add("itemId:" + i.getItemId());
        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_MOST_EARLY;
    }

    @Override
    public boolean removeSpaces() {
        return true;
    }

    @Override
    public String[] createNames() {
        return new String[]{"itemId"};
    }

}
