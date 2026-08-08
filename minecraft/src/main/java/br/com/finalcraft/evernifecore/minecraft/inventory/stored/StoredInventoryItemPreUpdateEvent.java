package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import org.bukkit.inventory.ItemStack;

/**
 * A change to a slot BEFORE it happens, and the chance to refuse it.
 *
 * <pre>{@code
 * backpack.onPreUpdate(event -> {
 *     if (event.getCause() == UpdateCause.PLAYER && event.getRemovedAmount() > 0 && locked) {
 *         event.setCancelled(true);
 *     }
 * });
 * }</pre>
 *
 * <p>Cancelling refuses the whole gesture, not the slot: the click never reaches the world, so nothing
 * leaves the cursor and nothing leaves the slot. That is the only answer that keeps the count right -
 * letting half a gesture through is how an item is duplicated or lost.</p>
 *
 * <p>A handler that throws is read as a refusal. It is a bug either way, and of the two ways to be
 * wrong, a screen that will not move an item is the one that can be seen.</p>
 */
public final class StoredInventoryItemPreUpdateEvent extends StoredInventoryItemUpdateEvent {

    private boolean cancelled = false;

    StoredInventoryItemPreUpdateEvent(StoredInventory inventory, UpdateCause cause, int slot, ItemStack previous,
                       ItemStack current) {
        super(inventory, cause, slot, previous, current);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

}
