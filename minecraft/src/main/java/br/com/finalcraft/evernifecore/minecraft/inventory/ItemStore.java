package br.com.finalcraft.evernifecore.minecraft.inventory;

import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * What a gui's editable region reads from and writes back into: slots numbered from zero, and the few
 * questions the framework has to ask before it moves an item.
 *
 * <p>There are two of them. A {@link GenericInventory} through {@link #of(GenericInventory)} is the
 * plain one - unbounded, silent, every stack as big as the item allows. A {@link StoredInventory} is
 * the one that answers back: it has a capacity, a maximum stack size per slot, and update handlers
 * that see a change before it happens and can refuse it.</p>
 *
 * <p>The three defaults below are what "plain" means, and they are what makes the second kind
 * optional: a store that overrides none of them behaves exactly as this framework behaved before they
 * existed.</p>
 */
public interface ItemStore {

    /** What {@link #getCapacity()} answers when nothing bounds the store. */
    int UNBOUNDED = Integer.MAX_VALUE;

    /** How many slots this store has, counting from zero, or {@link #UNBOUNDED}. */
    int getCapacity();

    /** What {@code slot} holds, as a copy, or {@code null}. */
    @Nullable
    ItemStack getItem(int slot);

    /**
     * Writes {@code slot} without asking and without telling: no update handler runs.
     *
     * <p>It is what the framework uses to put back what a container already holds - the gesture was
     * judged and carried out ticks ago, so asking now would be asking about the past. Code that means
     * to CHANGE a store wants {@link StoredInventory#setItem(UpdateCause, int, ItemStack)}, which asks
     * first and reports afterwards.</p>
     */
    void setItemSilently(int slot, @Nullable ItemStack item);

    /**
     * Every slot holding something, in no particular order. It is how a region of {@code n} slots can
     * say what of its store it cannot reach - a question {@link #UNBOUNDED} makes unanswerable by
     * counting.
     */
    @Nonnull
    int[] getOccupiedSlots();

    /** How much of {@code item} one stack of {@code slot} holds. The item's own maximum, by default. */
    default int getMaxStackSize(int slot, @Nullable ItemStack item) {
        return maxStackSizeOf(item);
    }

    /**
     * Whether this store judges its own updates - which is what makes the framework ask before letting
     * a gesture through, instead of only reading the result afterwards.
     */
    default boolean vetsUpdates() {
        return false;
    }

    /**
     * What two screens would really be sharing if they both used this store: itself, unless it only
     * stands for something else.
     *
     * <p>Two regions on one inventory lose each other's items, and the framework can only say so if it
     * recognises the second one. A store that WRAPS something - one inventory, two wrappers - is not
     * recognisable by its own identity, so it answers what it wraps.</p>
     */
    @Nonnull
    default Object getSharedSource() {
        return this;
    }

    /**
     * Asks whether {@code slot} may go from {@code previous} to {@code next}. Answering {@code false}
     * cancels the gesture whole: nothing moves, on either side of it.
     */
    default boolean mayUpdate(@Nonnull UpdateCause cause, int slot, @Nullable ItemStack previous,
                              @Nullable ItemStack next) {
        return true;
    }

    /** Tells the store {@code slot} has changed. Informative - the change already happened. */
    default void reportUpdate(@Nonnull UpdateCause cause, int slot, @Nullable ItemStack previous,
                              @Nullable ItemStack next) {

    }

    /**
     * A {@link GenericInventory} as a store. The inventory itself is untouched - it is mirrored on
     * another platform and composed into player data everywhere, so what it lacks is added around it.
     */
    @Nonnull
    static ItemStore of(@Nonnull GenericInventory inventory) {
        return new GenericInventoryStore(inventory);
    }

    /**
     * Whether {@code item} is nothing at all: no stack, an empty one, or the AIR a container answers
     * with instead of {@code null} on some versions. The three ways of holding nothing, in one place,
     * because a slot judged empty by one rule and occupied by another is how an item goes missing.
     */
    static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /** What one stack of {@code item} holds on this server, or 64 when it will not say. */
    static int maxStackSizeOf(@Nullable ItemStack item) {
        if (item != null) {
            try {
                int max = item.getMaxStackSize();
                if (max > 0) {
                    return max;
                }
            } catch (Throwable unanswerable) {
                //an item type this server cannot measure still has to be movable
            }
        }
        return 64;
    }

}
