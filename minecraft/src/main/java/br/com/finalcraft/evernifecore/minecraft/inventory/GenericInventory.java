package br.com.finalcraft.evernifecore.minecraft.inventory;

import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;

public class GenericInventory {

    protected final HashMap<Integer, ItemInSlot> items = new HashMap<>();

    public GenericInventory() {

    }

    public GenericInventory(Collection<ItemInSlot> itemsInSlots) {
        for (ItemInSlot itemInSlot : itemsInSlots) {
            this.items.put(itemInSlot.getSlot(), itemInSlot);
        }
    }

    public Collection<ItemInSlot> getItems() {
        return items.values();
    }

    public ItemStack getItem(int index){
        ItemInSlot itemInSlot = items.get(index);
        return itemInSlot != null ? itemInSlot.getItemStack() : null;
    }

    public void removeItem(int index){
        items.remove(index);
    }

    public void setItem(int index, ItemStack itemStack){
        if (itemStack == null){
            removeItem(index);
        }else {
            items.put(index, new ItemInSlot(index, itemStack));
        }
    }

    public void restoreTo(Inventory inventory){
        ItemStack[] inventoryContent = new ItemStack[inventory.getSize()];

        for (int i = 0; i < inventoryContent.length; i++) {
            ItemStack fcItemStack = getItem(i);
            inventoryContent[i] = fcItemStack != null ? fcItemStack : null;
        }

        inventory.setContents(inventoryContent);
    }

}
