package br.com.finalcraft.evernifecore.hytale.inventory;

import br.com.finalcraft.evernifecore.hytale.inventory.data.ItemInSlot;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.Collection;
import java.util.HashMap;

public class GenericInventory {

    protected final HashMap<Integer, ItemInSlot> items = new HashMap<>();

    public GenericInventory() {

    }

    public GenericInventory(ItemContainer itemContainer){
        for (short slot = 0; slot < itemContainer.getCapacity(); slot++) {
            ItemStack itemStack = itemContainer.getItemStack(slot);
            if (itemStack != null && itemStack.isValid()){
                this.items.put((int) slot, new ItemInSlot(slot, itemStack));
            }
        }
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

    public void restoreTo(ItemContainer itemContainer){
        for (short i = 0; i < itemContainer.getCapacity(); i++) {
            ItemStack itemStack = getItem(i);
            itemContainer.setItemStackForSlot(i, itemStack != null ? itemStack : ItemStack.EMPTY);
        }
    }

}
