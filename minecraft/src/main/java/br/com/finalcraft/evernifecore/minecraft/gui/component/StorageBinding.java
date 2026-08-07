package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.view.StorageContext;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An area the viewer may really take items out of and put items into, kept in a
 * {@link GenericInventory}.
 *
 * <p>It is the one place in the framework where a click is not cancelled, so the rule is the reverse
 * of everywhere else: nothing is allowed until it is named, one gesture at a time.
 * {@link #policy(ClickPolicy)} says which gestures, {@link #denyPlace(Predicate)} which items.</p>
 *
 * <pre>{@code
 * storage(l -> l.AREA)
 *         .backedBy(backpack.getContents())
 *         .policy(ClickPolicy.builder().allowTake().allowPlace().allowSwap().allowDrag().build())
 *         .denyPlace(item -> Blacklist.contains(item))
 *         .onChange(ctx -> backpack.markDirty());
 * }</pre>
 *
 * <p>The region's own indexes are {@code 0..n-1}, in the order its slots were declared, and the store
 * behind it is addressed as a container of exactly that many slots - which is where the capacity a
 * {@link GenericInventory} does not have comes from. Both directions copy: what the store hands over
 * is cloned before it reaches the open window, and what the window holds is cloned before it reaches
 * the store, so the live stack of neither is ever the stack of the other.</p>
 *
 * <p><b>The invariant.</b> Over any sequence of clicks the sum of the region, the viewer's own
 * inventory and their cursor does not change: nothing is created and nothing is lost. Three things buy
 * it. Nothing paints over an editable slot - the buffer disowns it, so a render, a resync or a replaced
 * container can neither blank it nor duplicate what is in it. Every gesture the framework has to take
 * apart itself - a drag divided slot by slot, a shift-click arriving from below - adds to the region
 * exactly what it takes from the source, in one step. And the store is only ever written from what the
 * container actually holds, so the two can differ by no more than the tick between a click and the
 * read that follows it.</p>
 *
 * <p>One case it does not cover: two screens open on the same {@link GenericInventory} at once. Each
 * seeds its own container from the same items and the last to close is the one whose contents survive.
 * The framework says so in the log and does not prevent it.</p>
 */
public final class StorageBinding {

    private final String name;
    private final SlotSet slots;

    private GenericInventory backing;
    private ClickPolicy policy = ClickPolicy.DENY_ALL;
    private Predicate<ItemStack> placeFilter;
    private Consumer<StorageContext> onChange;

    public StorageBinding(@Nonnull String name, @Nonnull SlotSet slots) {
        this.name = name;
        this.slots = slots;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declaration
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The store this region is filled from when it opens and written back into after every change.
     *
     * <p>It has to be the one the plugin actually persists: the framework saves nothing on its own, and
     * a store built on the spot is thrown away with the screen.</p>
     */
    @Nonnull
    public StorageBinding backedBy(@Nonnull GenericInventory backing) {
        if (backing == null) {
            throw new IllegalArgumentException("The storage region [" + name + "] was given no store to "
                    + "keep its contents in. Hand over the GenericInventory the plugin persists - "
                    + "backedBy(backpack.getContents()) - or the region has nowhere to read from and "
                    + "nowhere to write back to.");
        }
        this.backing = backing;
        return this;
    }

    /**
     * Which gestures go through here. Whatever is not named is cancelled; {@link ClickPolicy#EDIT_ALL}
     * is the shorthand for everything a player needs in order to move an item.
     *
     * <p>Two gestures are judged by this framework and not by the platform. A shift-move is a
     * {@code TAKE} when it leaves the region and a {@code PLACE} when it arrives from the viewer's own
     * inventory, so what the policy is asked about is the direction. And a double click is refused
     * whatever the policy says: it gathers every matching stack of the whole window at once, this
     * screen's own icons included, which no per-slot rule can express.</p>
     */
    @Nonnull
    public StorageBinding policy(@Nonnull ClickPolicy policy) {
        this.policy = policy == null ? ClickPolicy.DENY_ALL : policy;
        return this;
    }

    /**
     * Refuses every item {@code filter} answers {@code true} for.
     *
     * <p>Filters add up instead of replacing each other: an item gets in only when every filter
     * declared here lets it. That is the safe way round for an area where a mistake is a lost or a
     * duplicated item.</p>
     */
    @Nonnull
    public StorageBinding denyPlace(@Nonnull Predicate<ItemStack> filter) {
        requireFilter(filter);
        return alsoRequire(filter.negate());
    }

    /** Accepts only the items {@code filter} accepts - see {@link #denyPlace(Predicate)} on how they add up. */
    @Nonnull
    public StorageBinding allowPlace(@Nonnull Predicate<ItemStack> filter) {
        requireFilter(filter);
        return alsoRequire(filter);
    }

    /**
     * Runs after every accepted change, on the tick that follows it, and once more when the screen goes
     * away for any reason - closed, reloaded, disconnected, shut down. Persisting is the consumer's
     * call: {@link StorageContext#isLast()} is how one that saves lazily knows to flush.
     */
    @Nonnull
    public StorageBinding onChange(@Nullable Consumer<StorageContext> onChange) {
        this.onChange = onChange;
        return this;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    /** The layout key this region was declared under, or a generated one when it was given raw slots. */
    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public SlotSet getSlots() {
        return slots;
    }

    /** The store, or {@code null} while nobody has said which one it is. */
    @Nullable
    public GenericInventory getBacking() {
        return backing;
    }

    @Nonnull
    public ClickPolicy getPolicy() {
        return policy;
    }

    @Nullable
    public Consumer<StorageContext> getOnChange() {
        return onChange;
    }

    /**
     * Every filter declared here as one predicate, or {@code null} when nothing is filtered. The view
     * takes it when it opens, so the item rule a click is judged by is fixed at that moment.
     */
    @Nullable
    public Predicate<ItemStack> getPlaceFilter() {
        return placeFilter;
    }

    @Override
    public String toString() {
        return "StorageBinding{" + name + ", slots=" + slots + "}";
    }

    private StorageBinding alsoRequire(Predicate<ItemStack> filter) {
        this.placeFilter = this.placeFilter == null ? filter : this.placeFilter.and(filter);
        return this;
    }

    private void requireFilter(Predicate<ItemStack> filter) {
        if (filter == null) {
            throw new IllegalArgumentException("The storage region [" + name + "] was given a null item "
                    + "filter. Drop the call to let every item in, and keep the gesture rules in policy(...).");
        }
    }

}
