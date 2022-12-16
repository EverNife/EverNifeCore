package br.com.finalcraft.evernifecore.itemstack.invitem;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.itemstack.invitem.imp.InvItemDraconicChest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class InvItem {

    protected final Material material;

    public InvItem() {
        this.material = calculateMaterial();
    }

    protected abstract @Nullable Material calculateMaterial();

    public abstract Boolean checkIfShouldBeEnabled();

    public abstract String getName();

    public abstract List<ItemInSlot> getItemsFrom(ItemStack itemStack);

    public abstract ItemStack createChestWithItems(ItemStack customChest, List<ItemInSlot> itemInSlots);

    public Material getMaterial() {
        return material;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Static Methods
    // -----------------------------------------------------------------------------------------------------------------

    public static HashMap<Material, InvItem> INV_MATERIALS_MAP = new HashMap<>();
    public static HashMap<String, InvItem> INV_NAME_MAP = new HashMap<>();

    static {
        EverNifeCore.info("Checking for InvItems!");

        List<InvItem> BUIT_IN_INV_ITEMS = Arrays.asList(
                new InvItemDraconicChest()
        );

        for (InvItem invItem : BUIT_IN_INV_ITEMS) {
            boolean isEnabled = invItem.getMaterial() != null;
            EverNifeCore.info("[InvItem] <" + invItem.getName() + "> ENABLED: " + isEnabled);
            if (isEnabled){
                INV_MATERIALS_MAP.put(invItem.getMaterial(), invItem);
                INV_NAME_MAP.put(invItem.getName(), invItem);
            }
        }
    }

    public static @Nullable InvItem of(Material material){
        return INV_MATERIALS_MAP.get(material);
    }

    public static @Nullable InvItem of(String invItemName){
        return INV_NAME_MAP.get(invItemName);
    }
}
