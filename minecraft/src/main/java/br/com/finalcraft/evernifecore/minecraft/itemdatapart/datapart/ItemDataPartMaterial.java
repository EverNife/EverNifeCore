package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDataPartMaterial extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        return FCItemFactory.from(argument).build();
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getType() == other_item.getType();
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        output.add("type:" + i.getType().name());
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
        return new String[]{"type", "id", "material", "identifier"};
    }

}
