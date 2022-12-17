package br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class TinkersInvFactory implements IExtraInvFactory {

    @Override
    public int getInvMaxSize() {
        return 8;
    }

    @Override
    public String getId() {
        return "tinkers";
    }

    @Override
    public ExtraInv getPlayerExtraInv(Player player) {
        return new ExtraInv(this, ItemInSlot.fromStacks(EverForgeLibIntegration.getTinkersInventory(player)));
    }

    @Override
    public void setPlayerExtraInv(Player player, ExtraInv extraInv) {
        ItemStack[] inventoryContent = Arrays.copyOf(ItemInSlot.toArray(extraInv.getItems()),this.getInvMaxSize());
        EverForgeLibIntegration.setTinkersInventory(player, inventoryContent);
    }
}
