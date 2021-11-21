package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemDataPartDurability extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        int damage = FCInputReader.parseInt(argument, -1);

        if (damage == -1) {
            EverNifeCore.warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'. " +
                    "It needs to be an integer number like '0', '5' or '200'. ");
            return item;
        }

        if (MCVersion.isCurrentEqualOrHigher(MCVersion.v1_13_R1)){
            if (!(item.getItemMeta() instanceof Damageable)) {
                EverNifeCore.warning("Mistake in Config: Unable to add damage/durability to items of type '" + item.getType() + "'.");
                return item;
            }
            Damageable d = (Damageable) item.getItemMeta();
            d.setDamage(damage);

            item.setItemMeta((ItemMeta) d);
        }else {
            item.setDurability((short) damage);;
        }

        return item;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        if (MCVersion.isCurrentEqualOrHigher(MCVersion.v1_13_R1)){
            if (base_item.getItemMeta() instanceof Damageable != other_item.getItemMeta() instanceof Damageable) {
                return false;
            }
            if (base_item.getItemMeta() instanceof Damageable) {
                Damageable a = (Damageable) base_item.getItemMeta();
                Damageable b = (Damageable) other_item.getItemMeta();
                if (a.getDamage() != b.getDamage()) {
                    return false;
                }
            }
            return true;
        }else {
            return base_item.getDurability() == other_item.getDurability();
        }
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        if (MCVersion.isCurrentEqualOrHigher(MCVersion.v1_13_R1)){
            if (itemStack.hasItemMeta()) {
                if (itemStack.getItemMeta() instanceof Damageable) {
                    Damageable d = (Damageable) itemStack.getItemMeta();
                    output.add("durability:" + d.getDamage());
                }
            }
        }else {
            itemStack.getDurability();
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
        return new String[]{"damage", "durability", "subid"};
    }

}
