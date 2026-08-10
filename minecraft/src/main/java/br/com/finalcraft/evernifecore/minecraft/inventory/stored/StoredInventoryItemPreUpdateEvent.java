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
 * <p>What this event carries is a PREDICTION: the click has not been applied yet, so the item is the
 * one the gesture is expected to leave behind. {@link StoredInventoryItemPostUpdateEvent} carries the MEASUREMENT -
 * what the slot really holds once the platform is done - and the two can differ whenever the platform
 * decides how much of a stack actually moves. A handler that has to charge, count or record something
 * belongs in the post event; the pre event is for saying no.</p>
 *
 * <p>Cancelling a click refuses the whole click: it never reaches the world, so nothing leaves the
 * cursor and nothing leaves the slot. A gesture the framework has to take apart itself - a drag divided
 * between slots, a shift-click poured over them - is refused one slot at a time instead: the slot that
 * says no receives nothing and its share stays where it was.</p>
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
