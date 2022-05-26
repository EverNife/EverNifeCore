package br.com.finalcraft.evernifecore.integration.bossshop.datapart;

import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.managers.item.ItemDataPart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDataPartNBT extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.transform(item, used_name, argument);
    }

    @Override
    public int getPriority() {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.getPriority();
    }

    @Override
    public boolean removeSpaces() {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.removeSpaces();
    }

    @Override
    public String[] createNames() {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.createNames();
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.read(i, output);
    }

    @Override
    public boolean isSimilar(ItemStack shop_item, ItemStack player_item, BSBuy buy, Player p) {
        return true; //Too expensive to check
    }

}
