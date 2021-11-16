package br.com.finalcraft.evernifecore.integration.bossshop.datapart;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.managers.item.ItemDataPart;
import org.bukkit.entity.Player;
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

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        if (NMSUtils.get().hasNBTTagCompound(i)){
            output.add("nbt: '" + NMSUtils.get().getNBTtoString(i) + "'");
        }
        return output;
    }

    @Override
    public boolean isSimilar(ItemStack shop_item, ItemStack player_item, BSBuy buy, Player p) {
        return true; //To expensive to check
    }

}
