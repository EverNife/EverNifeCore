package br.com.finalcraft.evernifecore.fcitemstack.iteminv;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerinventory.ItemSlot;
import br.com.finalcraft.evernifecore.fcitemstack.FCItemStack;
import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;

public enum InvItem {
    DRACONIC_CHEST("DRACONICEVOLUTION_DRACONIUMCHEST",
            "DRACONICEVOLUTION_DRACONIUM_CHEST"){
        @Override
        public List<ItemSlot> getItemsFrom(ItemStack draconiumChest) {
            ItemStack[] inventory = EverForgeLibIntegration.getDraconicChestIventory(draconiumChest);
            return ItemSlot.fromStackList(inventory);
        }

        @Override
        public void setItemsTo(FCItemStack draconiumChest, List<ItemSlot> itemSlots) {
            ItemStack[] inventory = ItemSlot.toStackList(itemSlots);
            ItemStack result = EverForgeLibIntegration.setDraconicChestInventory(draconiumChest.getItemStack(), inventory);
            draconiumChest.setItemStack(result);
        }

        @Override
        public Boolean checkIfShouldBeEnabled() {
            return EverForgeLibIntegration.draconicLoaded && material != null;
        }
    };

    protected Boolean enabled = null;
    protected final Material material;

    InvItem(String... materialName) {
        Material tempMaterial = null;
        for (String name : materialName) {
            tempMaterial = Material.getMaterial(name);
            if (tempMaterial != null) break;
        }
        this.material = tempMaterial;
//        this.material = Arrays.asList(materialName).stream().map(Material::getMaterial).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public abstract List<ItemSlot> getItemsFrom(ItemStack itemStack);
    public abstract void setItemsTo(FCItemStack itemStack, List<ItemSlot> itemSlots);
    public abstract Boolean checkIfShouldBeEnabled();

    public boolean isEnabled(){
        return enabled != null ? enabled : (enabled = checkIfShouldBeEnabled());
    }

    public Material getMaterial() {
        return material;
    }

    private static HashMap<Material, InvItem> mapOfInvItems = null;

    public static InvItem getInvItem(ItemStack itemStack){
        if (mapOfInvItems == null) {
            mapOfInvItems = new HashMap<>();
            EverNifeCore.info("Checking for InvItems!");
            for (InvItem invItem : values()) {
                EverNifeCore.info("[InvItem] " + invItem.name() + " ENABLED: " + invItem.isEnabled());
                if (invItem.isEnabled()){
                    mapOfInvItems.put(invItem.getMaterial(), invItem);
                }
            }
        }
        return mapOfInvItems.size() == 0 ? null : mapOfInvItems.get(itemStack.getType());
    }
}
