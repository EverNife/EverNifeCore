package br.com.finalcraft.evernifecore.inventory.invitem.imp;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class InvItemDraconicChest implements InvItem {

    private final Material material;

    public InvItemDraconicChest() {
        this.material = MCVersion.isEqual(MCVersion.v1_7_10) ? FCInputReader.parseMaterial("DRACONICEVOLUTION_DRACONIUMCHEST")
                : MCVersion.isEqual(MCVersion.v1_12) ? FCInputReader.parseMaterial("DRACONICEVOLUTION_DRACONIUM_CHEST")
                : null;

        if (this.material == null){
            throw new IllegalStateException("There is no Draconic Chest found on this Server!");
        }
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public String getId() {
        return "DRACONIC_CHEST";
    }

    @Override
    public List<ItemInSlot> extractFromItemStack(ItemStack draconiumChest) {
        ItemStack[] inventory = EverForgeLibIntegration.getDraconicChestIventory(draconiumChest);
        return ItemInSlot.fromStacks(inventory);
    }

    @Override
    public ItemStack applyToItemStack(ItemStack draconiumChest, List<ItemInSlot> itemInSlots) {
        ItemStack[] inventory = ItemInSlot.toArray(itemInSlots);
        ItemStack result = EverForgeLibIntegration.setDraconicChestInventory(draconiumChest, inventory);
        return result;
    }

}
