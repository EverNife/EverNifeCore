package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemMatcher;

/**
 * The player's own inventory, {@value #ID}: each platform registers its one on enable. Besides receiving items it
 * counts and takes them, which is what an {@code item} cost and an item contribution need. Callable from any thread:
 * a platform hops to the player's thread and waits.
 */
public interface InventoryDestination extends DeliveryDestination {

    String ID = "evernifecore:inventory";

    @Override
    default String getId() {
        return ID;
    }

    /** How many items {@code matcher} accepts {@code player} holds. */
    int count(FPlayer player, ItemMatcher matcher);

    /**
     * Takes {@code amount} matching items from {@code player}, all or nothing.
     *
     * @return false, taking nothing, when the player holds fewer
     */
    boolean take(FPlayer player, ItemMatcher matcher, int amount);
}
