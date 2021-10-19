package br.com.finalcraft.evernifecore.config.playerinventory;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.fcitemstack.FCItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class GenericInventory implements Config.Salvable {

    protected final HashMap<Integer,ItemSlot> items = new HashMap<>();

    public GenericInventory(Collection<ItemSlot> itemSlots) {
        for (ItemSlot itemSlot : itemSlots) {
            this.items.put(itemSlot.slot, itemSlot);
        }
    }

    @Override
    public void onConfigSave(Config config, String path) {
        config.setValue(path + ".items", null); //Clear content before saving it
        for (ItemSlot itemSlot : items.values()) {
            config.setValue(path + ".items." + itemSlot.getSlot(), itemSlot.getFcItemStack());
        }
    }

    @Config.Loadable
    public static GenericInventory onConfigLoad(Config config, String path){
        List<ItemSlot> items = new ArrayList<>();

        for (String key : config.getKeys(path + ".items")) {
            try {
                Integer slot = Integer.parseInt(key);
                FCItemStack fcItemStack = config.getFCItem(path + ".items." + slot);
                ItemSlot itemSlot = new ItemSlot(slot,fcItemStack);
                items.add(itemSlot);
            }catch (NumberFormatException e){
                e.printStackTrace();
            }
        }

        return new GenericInventory(items);
    }

    public Collection<ItemSlot> getItems() {
        return items.values();
    }

    public FCItemStack getItem(int index){
        ItemSlot itemSlot = items.get(index);
        return itemSlot != null ? itemSlot.getFcItemStack() : null;
    }

    public void restoreTo(Inventory inventory){

        ItemStack[] inventoryContent = new ItemStack[inventory.getSize()];

        for (int i = 0; i < inventoryContent.length; i++) {
            FCItemStack fcItemStack = getItem(i);
            inventoryContent[i] = fcItemStack != null ? fcItemStack.getItemStack() : null;
        }

        inventory.setContents(inventoryContent);
    }

}
