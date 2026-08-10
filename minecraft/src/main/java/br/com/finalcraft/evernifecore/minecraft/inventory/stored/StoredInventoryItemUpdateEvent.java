package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

/**
 * One slot of a {@link StoredInventory} going from one item to another: which slot, what was there,
 * what is going there, and who is behind it.
 *
 * <p>The two amounts are the reason this exists at all. A handler that only sees "slot 7 changed" has
 * to work out how much moved before it can decide anything, and getting that arithmetic wrong is how a
 * shop charges for the wrong number of items. {@link #getRemovedAmount()} and {@link #getAddedAmount()}
 * answer it once, here: a stack of five becoming a stack of two removed three, and a stack of five
 * becoming a stack of two of something ELSE removed five and added two.</p>
 */
public abstract class StoredInventoryItemUpdateEvent {

    private final StoredInventory inventory;
    private final UpdateCause cause;
    private final int slot;
    private final ItemStack previous;
    private final ItemStack current;

    StoredInventoryItemUpdateEvent(StoredInventory inventory, UpdateCause cause, int slot, ItemStack previous,
                    ItemStack current) {
        this.inventory = inventory;
        this.cause = cause;
        this.slot = slot;
        this.previous = previous;
        this.current = current;
    }

    /** The inventory this slot belongs to - what tells two of them apart when one handler serves both. */
    @Nonnull
    public StoredInventory getInventory() {
        return inventory;
    }

    @Nonnull
    public UpdateCause getCause() {
        return cause;
    }

    /** The slot inside the inventory, which is not the slot of whatever window is showing it. */
    public int getSlot() {
        return slot;
    }

    /** What the slot held, as a copy, or {@code null} when it was empty. */
    @Nullable
    public ItemStack getPreviousItem() {
        return copyOf(previous);
    }

    /** What the slot is going to hold, as a copy, or {@code null} when it is being emptied. */
    @Nullable
    public ItemStack getNewItem() {
        return copyOf(current);
    }

    /** How many units are joining the slot: the whole new stack when the item itself changes. */
    public int getAddedAmount() {
        if (isEmpty(current)) {
            return 0;
        }
        if (isEmpty(previous) || !previous.isSimilar(current)) {
            return current.getAmount();
        }
        return Math.max(0, current.getAmount() - previous.getAmount());
    }

    /** How many units are leaving the slot: the whole old stack when the item itself changes. */
    public int getRemovedAmount() {
        if (isEmpty(previous)) {
            return 0;
        }
        if (isEmpty(current) || !previous.isSimilar(current)) {
            return previous.getAmount();
        }
        return Math.max(0, previous.getAmount() - current.getAmount());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{slot=" + slot + ", cause=" + cause
                + ", -" + getRemovedAmount() + " +" + getAddedAmount() + "}";
    }

    static ItemStack copyOf(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    static boolean isEmpty(ItemStack item) {
        return ItemStore.isEmpty(item);
    }

}
