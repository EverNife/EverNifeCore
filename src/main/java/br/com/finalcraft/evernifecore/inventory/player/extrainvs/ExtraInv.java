package br.com.finalcraft.evernifecore.inventory.player.extrainvs;

import br.com.finalcraft.evernifecore.inventory.data.ItemSlot;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public abstract class ExtraInv {

    public final List<ItemSlot> itemSlotList = new ArrayList<>();

    public List<ItemSlot> getItemSlotList() {
        return itemSlotList;
    }

    public abstract int getInvMaxSize();

    public abstract ExtraInvType getType();

    public abstract String getName();

    public abstract ItemStack[] getPlayerExtraInv(Player player);

    public abstract void setPlayerExtraInv(Player player);

}
