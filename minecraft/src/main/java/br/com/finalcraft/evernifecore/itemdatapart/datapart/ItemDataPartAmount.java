package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDataPartAmount extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack itemStack, String used_name, String argument) {
        Integer amount = FCInputReader.parseInt(argument, null);
        if (amount == null) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. It needs to be a number like '1', '12' or '64'.");
            return itemStack;
        }
        itemStack.setAmount(amount);
        return itemStack;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getAmount() == other_item.getAmount();
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        output.add("amount:" + itemStack.getAmount());
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
        return new String[]{"amount", "number"};
    }

}
