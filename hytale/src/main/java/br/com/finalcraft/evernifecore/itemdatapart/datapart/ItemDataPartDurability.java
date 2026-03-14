package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.List;

public class ItemDataPartDurability extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        double durability = FCInputReader.parseDouble(argument, -1D);

        if (durability == -1) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. " +
                    "It needs to be a number like '0', '5.2' or '200'. ");
            return item;
        }

        return item.withDurability(durability);
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getDurability() == other_item.getDurability();
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        double durability = itemStack.getDurability();
        if (durability != 0){
            output.add("durability:" + durability);
        }
        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_EARLY;
    }

    @Override
    public boolean removeSpaces() {
        return true;
    }

    @Override
    public String[] createNames() {
        return new String[]{"durability"};
    }

}
