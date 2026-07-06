package br.com.finalcraft.evernifecore.minecraft.inventory;

import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists as a self-describing {@code {<slot>: ItemStack}} object through Jackson's
 * {@link JsonAnyGetter}/{@link JsonAnySetter}: each slot becomes a top-level key whose value routes through the
 * registered {@code ItemStack} adapter. Because it is a plain bound entity (no central registration), it
 * composes for free wherever it is nested (a field, a map value, an {@code extra.<id>} sub-inventory).
 */
public class GenericInventory {

    protected final HashMap<Integer, ItemInSlot> items = new HashMap<>();

    public GenericInventory() {

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

    public void restoreTo(Inventory inventory){
        ItemStack[] inventoryContent = new ItemStack[inventory.getSize()];

        for (int i = 0; i < inventoryContent.length; i++) {
            ItemStack fcItemStack = getItem(i);
            inventoryContent[i] = fcItemStack != null ? fcItemStack : null;
        }

        inventory.setContents(inventoryContent);
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
