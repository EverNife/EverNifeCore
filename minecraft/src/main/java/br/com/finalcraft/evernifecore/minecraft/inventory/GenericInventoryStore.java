package br.com.finalcraft.evernifecore.minecraft.inventory;

import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * A {@link GenericInventory} seen as an {@link ItemStore}: unbounded, silent, and holding whatever it
 * is handed.
 *
 * <p>The copies are this class's doing, not the inventory's. {@code GenericInventory.setItem} keeps the
 * reference it is given, so a stack written through it would still be the one the open window holds -
 * the two would then change together, and a change made in one place would already be true in the
 * other before anybody read it back.</p>
 */
public final class GenericInventoryStore implements ItemStore {

    private final GenericInventory inventory;

    GenericInventoryStore(@Nonnull GenericInventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("A GenericInventory store needs the inventory it stands for.");
        }
        this.inventory = inventory;
    }

    /** The inventory behind this store - what the plugin persists. */
    @Nonnull
    public GenericInventory getInventory() {
        return inventory;
    }

    @Override
    public int getCapacity() {
        return UNBOUNDED;
    }

    @Override
    @Nonnull
    public Object getSharedSource() {
        return inventory;
    }

    @Override
    @Nullable
    public ItemStack getItem(int slot) {
        ItemStack held = inventory.getItem(slot);
        return held == null ? null : held.clone();
    }

    @Override
    public void setItemSilently(int slot, @Nullable ItemStack item) {
        if (item == null) {
            inventory.removeItem(slot);
        } else {
            inventory.setItem(slot, item.clone());
        }
    }

    @Override
    @Nonnull
    public int[] getOccupiedSlots() {
        Collection<ItemInSlot> items = inventory.getItems();
        int[] slots = new int[items.size()];
        int size = 0;
        for (ItemInSlot itemInSlot : items) {
            slots[size++] = itemInSlot.getSlot();
        }
        return slots;
    }

    @Override
    public String toString() {
        return "GenericInventoryStore{" + inventory + "}";
    }

}
