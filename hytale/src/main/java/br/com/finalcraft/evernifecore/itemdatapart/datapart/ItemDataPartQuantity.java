package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.List;

public class ItemDataPartQuantity extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack itemStack, String used_name, String argument) {
        Integer amount = FCInputReader.parseInt(argument, null);
        if (amount == null) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. It needs to be a number like '1', '12' or '64'.");
            return itemStack;
        }
        return itemStack.withQuantity(amount);
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getQuantity() == other_item.getQuantity();
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        output.add("quantity:" + itemStack.getQuantity());
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
        return new String[]{"quantity","amount"};
    }

}
