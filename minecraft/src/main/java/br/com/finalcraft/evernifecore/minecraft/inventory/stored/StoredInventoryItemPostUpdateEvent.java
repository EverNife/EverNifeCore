package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import org.bukkit.inventory.ItemStack;

/**
 * A change to a slot that has already happened: the inventory answers the new item by the time this
 * runs, and nothing here can take it back.
 *
 * <p>This is where persisting belongs - {@code markDirty()}, a log line, a screen told to redraw -
 * because it fires once per slot that really changed and never for a gesture that was refused.</p>
 */
public final class StoredInventoryItemPostUpdateEvent extends StoredInventoryItemUpdateEvent {

    StoredInventoryItemPostUpdateEvent(StoredInventory inventory, UpdateCause cause, int slot, ItemStack previous,
                        ItemStack current) {
        super(inventory, cause, slot, previous, current);
    }

}
