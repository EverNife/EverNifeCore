package br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.wrappers;

import br.com.finalcraft.evernifecore.config.playerinventory.ItemSlot;
import br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.ExtraInvType;
import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BaublesInv extends ExtraInv {

    private static final int SIZE = MCVersion.isLegacy() ? 4 : 7;
    @Override
    public int getInvMaxSize() {
        return SIZE;
    }

    @Override
    public String getName() {
        return "baubles";
    }

    @Override
    public ItemStack[] getPlayerExtraInv(Player player) {
        return EverForgeLibIntegration.getBaublesInventory(player);
    }

    @Override
    public void setPlayerExtraInv(Player player) {
        ItemStack[] baublesContent = new ItemStack[getInvMaxSize()];
        for (ItemSlot itemSlot : getItemSlotList()) {
            baublesContent[itemSlot.getSlot()] = itemSlot.getFcItemStack().copyItemStack();
        }
        EverForgeLibIntegration.setBaublesInventory(player, baublesContent);
    }

    @Override
    public ExtraInvType getType() {
        return ExtraInvType.BAUBLES;
    }
}
