package br.com.finalcraft.evernifecore.hytale.inventory;

import br.com.finalcraft.evernifecore.hytale.inventory.data.ItemInSlot;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists as a self-describing {@code {<slot>: ItemStack}} object through Jackson's
 * {@link JsonAnyGetter}/{@link JsonAnySetter}: each slot becomes a top-level key whose value routes through the
 * registered {@code ItemStack} adapter. Being a plain bound entity, it composes for free wherever nested.
 */
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

    @JsonIgnore
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

    // ==================== config shape ====================

    /** The write half: each occupied slot as a top-level {@code "<slot>": ItemStack} entry. */
    @JsonAnyGetter
    protected Map<String, ItemStack> toConfigSlots() {
        Map<String, ItemStack> slots = new LinkedHashMap<>();
        for (ItemInSlot itemInSlot : items.values()) {
            slots.put(String.valueOf(itemInSlot.getSlot()), itemInSlot.getItemStack());
        }
        return slots;
    }

    /** The read half: one call per top-level key, binding the value through the registered ItemStack adapter. */
    @JsonAnySetter
    protected void fromConfigSlot(String slot, ItemStack itemStack) {
        setItem(Integer.parseInt(slot), itemStack);
    }

}
