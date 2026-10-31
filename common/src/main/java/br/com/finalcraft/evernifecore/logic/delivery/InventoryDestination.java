package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import jakarta.annotation.Nullable;

import java.util.List;

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

    /**
     * Every stack of {@code player}'s main inventory (no armor, no off-hand), in slot order, each described with its
     * material, amount, name and lore - what a matcher reads. Empty when the player is not reachable.
     */
    List<ItemSpec> getContents(FPlayer player);

    /** The stack in {@code player}'s hand, described like {@link #getContents}; null for an empty hand. */
    @Nullable ItemSpec getHeld(FPlayer player);

    /** How many of {@code item} fit in {@code player}'s main inventory right now, stacking onto equal stacks first. */
    int roomFor(FPlayer player, ItemSpec item);
}
