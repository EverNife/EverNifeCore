package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDataPartMaterial extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        short durability = 0;
        Material material = Material.STONE;

        if (argument.contains(":")) { //Can be used for durability
            String[] parts = argument.split(":");
            if (parts.length > 1) {
                durability = FCInputReader.parseInt(parts[1].trim(), 0).shortValue();
            }
            argument = parts[0].trim();
        }

        material = FCInputReader.parseMaterial(argument);

        if (material == null) {
            EverNifeCore.warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. Unable to find a fitting material.");
            return item;
        }

        item.setType(material);
        if (durability != 0){
            item.setDurability(durability);
        }
        return item;
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
        return new String[]{"type", "id", "material"};
    }

}
