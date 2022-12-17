package br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class BaublesInvFactory implements IExtraInvFactory {

    private static final int SIZE = MCVersion.isEqual(MCVersion.v1_7_10) ? 4 : 7;

    @Override
    public int getInvMaxSize() {
        return SIZE;
    }

    @Override
    public String getId() {
        return "baubles";
    }

    @Override
    public ExtraInv getPlayerExtraInv(Player player) {
        return new ExtraInv(this, ItemInSlot.fromStacks(EverForgeLibIntegration.getBaublesInventory(player)));
    }

    @Override
    public void setPlayerExtraInv(Player player, ExtraInv extraInv) {
        ItemStack[] inventoryContent = Arrays.copyOf(ItemInSlot.toArray(extraInv.getItems()),this.getInvMaxSize());
        EverForgeLibIntegration.setBaublesInventory(player, inventoryContent);
    }
}
