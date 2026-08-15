package br.com.finalcraft.evernifecore.minecraft.api.events;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.Recipe;

/**
 * A default CraftItemEvent that will tell not only what recipe was used
 * but will prevent a lot of common mistakes when hearing to craft events.
 *
 * This event has two main infos: the Amount of times a Recipe has been
 * executed as well as the total amount of items produced.
 *
 * For example, if you SHIFT_CLICK a craft recipe with 64 logs on a Crafting
 * Table, the result will be 64 CraftTimes but 256 AmountProduced as each
 * WOOD_RECIPE produces 4 outputs.
 *
 * Produced only while somebody listens: the core registers its {@code CraftItemEvent} listener on the
 * first listener of this event and drops it with the last, whether that listener sits on the bus or on
 * the server.
 *
 * @author EverNife
 */
public class ECPlayerCraftItemEvent extends ECEvent implements ECCancellable {

    public static HandlerList getHandlerList() {
        return (HandlerList) ECEvent.getHandlerListOf(ECPlayerCraftItemEvent.class);
    }

    private final CraftItemEvent craftItemEvent;
    private final Player player;
    private final int craftTimes;
    private final int amountProduced;

    public ECPlayerCraftItemEvent(CraftItemEvent craftItemEvent, Player player, int craftTimes, int amountProduced) {
        this.craftItemEvent = craftItemEvent;
        this.player = player;
        this.craftTimes = craftTimes;
        this.amountProduced = amountProduced;
    }

    /**
     * Get the Original CraftItemEvent
     *
     * @return The {@link CraftItemEvent}
     * @author EverNife
     */
    public CraftItemEvent getOriginalEvent() {
        return craftItemEvent;
    }

    /**
     * Get the amount of times this {@link Recipe} has
     * been crafted. On usage of SHIFT_CLICK it can be
     * more than one.
     *
     * @return the amount of times this recipes was crafted
     * @author EverNife
     */
    public int getCraftTimes() {
        return craftTimes;
    }

    /**
     * Get the total amount of all ItemStacks produced
     * on this craft. It's the sum of all ItemStack.getAmount()
     * produced on this CraftEvent.
     *
     * @return the amount of items produced by this craft operation
     * @author EverNife
     */
    public int getAmountProduced() {
        return amountProduced;
    }

    /**
     * Get the recipe of this CraftEvent
     *
     * @return The recipe of this craft event
     * @author EverNife
     */
    public Recipe getRecipe() {
        return craftItemEvent.getRecipe();
    }

    @Override
    public boolean isCancelled() {
        return craftItemEvent.isCancelled();
    }

    /**
     * Get the player from this event
     *
     * @return The player that crafted
     * @author EverNife
     */
    public Player getPlayer() {
        return player;
    }

    @Override
    public void setCancelled(boolean cancel) {
        craftItemEvent.setCancelled(cancel);
    }

}
