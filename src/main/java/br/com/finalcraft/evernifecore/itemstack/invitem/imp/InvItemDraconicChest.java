package br.com.finalcraft.evernifecore.itemstack.invitem.imp;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.itemstack.invitem.InvItem;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InvItemDraconicChest extends InvItem {

    @Override
    public String getName() {
        return "DRACONIC_CHEST";
    }

    @Override
    protected @Nullable Material calculateMaterial() {
        if (EverForgeLibIntegration.draconicLoaded == false) return null;

        return MCVersion.isEqual(MCVersion.v1_7_10) ? FCInputReader.parseMaterial("DRACONICEVOLUTION_DRACONIUMCHEST")
                : MCVersion.isEqual(MCVersion.v1_12) ? FCInputReader.parseMaterial("DRACONICEVOLUTION_DRACONIUM_CHEST")
                : null;
    }

    @Override
    public Boolean checkIfShouldBeEnabled() {
        return EverForgeLibIntegration.draconicLoaded && material != null;
    }

    @Override
    public List<ItemInSlot> getItemsFrom(ItemStack draconiumChest) {
        ItemStack[] inventory = EverForgeLibIntegration.getDraconicChestIventory(draconiumChest);
        return ItemInSlot.fromStackList(inventory);
    }

    @Override
    public ItemStack createChestWithItems(ItemStack draconiumChest, List<ItemInSlot> itemInSlots) {
        ItemStack[] inventory = ItemInSlot.toStackList(itemInSlots);
        ItemStack result = EverForgeLibIntegration.setDraconicChestInventory(draconiumChest, inventory);
        return result;
    }

}
