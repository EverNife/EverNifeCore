package br.com.finalcraft.evernifecore.inventory.player.extrainvs.wrappers;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.data.ItemSlot;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.ExtraInvType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TinkersInv extends ExtraInv {

    @Override
    public int getInvMaxSize() {
        return 8;
    }

    @Override
    public String getName() {
        return "tinkers";
    }

    @Override
    public ItemStack[] getPlayerExtraInv(Player player) {
        return EverForgeLibIntegration.getTinkersInventory(player);
    }

    @Override
    public void setPlayerExtraInv(Player player) {
        ItemStack[] tinkers = new ItemStack[getInvMaxSize()];
        for (ItemSlot itemSlot : getItemSlotList()) {
            tinkers[itemSlot.getSlot()] = itemSlot.getItemStack().clone();
        }
        EverForgeLibIntegration.setTinkersInventory(player, tinkers);
    }

    @Override
    public ExtraInvType getType() {
        return ExtraInvType.TINKERS;
    }
}
