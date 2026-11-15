package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nullable;

/**
 * {@value #ID}: the item lands at the player's feet, through the platform's {@link InventoryDestination#drop}. A player
 * who is not online has no ground to drop on, so the whole item is handed back - and waits as a pending delivery.
 */
public final class GroundDestination implements DeliveryDestination {

    public static final String ID = "evernifecore:ground";
    public static final GroundDestination INSTANCE = new GroundDestination();

    private GroundDestination() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public @Nullable ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item) {
        FPlayer player = ctx.getPlayer();
        return player != null && DeliveryDestinations.requireInventory().drop(player, item) ? null : item;
    }

    @Override
    public String toString() {
        return ID;
    }
}
