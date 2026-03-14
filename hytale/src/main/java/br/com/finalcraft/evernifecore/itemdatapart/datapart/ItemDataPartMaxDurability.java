package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.List;

public class ItemDataPartMaxDurability extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        double maxDurability = FCInputReader.parseDouble(argument, -1D);

        if (maxDurability == -1) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. " +
                    "It needs to be a number like '0', '5.2' or '200'. ");
            return item;
        }

        return item.withMaxDurability(maxDurability);
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getMaxDurability() == other_item.getMaxDurability();
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        double maxDurability = itemStack.getMaxDurability();
        if (maxDurability != 0){
            output.add("maxDurability:" + maxDurability);
        }
        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_EARLY - 1;
    }

    @Override
    public boolean removeSpaces() {
        return true;
    }

    @Override
    public String[] createNames() {
        return new String[]{"maxDurability"};
    }

}
