package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An editable area: slots the player really takes items out of and puts items into. This is the contract every
 * renderer reads; only a renderer with {@link RendererCapability#STORAGE_SLOTS} can serve it, and the screen asks for
 * that capability the moment it declares one. Where the items live is the platform's own store, handed over with
 * {@link #backedBy(Object)} - an {@code ItemStore} or {@code GenericInventory} on Bukkit.
 *
 * <p>Nothing moves here until {@link #policy} says which gesture may.</p>
 */
public final class StorageBinding {

    private final String name;
    private final SlotSet slots;
    private Object backing;
    private ClickPolicy policy = ClickPolicy.DENY_ALL;
    private Predicate<ItemSpec> placeFilter;
    private Consumer<StorageChange> onChange;

    public StorageBinding(@Nonnull String name, @Nonnull SlotSet slots) {
        this.name = name;
        this.slots = slots;
    }

    /** The platform store the area reads from and writes back to. Its type is the renderer's to check. */
    @Nonnull
    public StorageBinding backedBy(@Nonnull Object store) {
        if (store == null) {
            throw new IllegalArgumentException("The storage region [" + name + "] was given no store to keep its contents "
                    + "in. Hand over the store the plugin persists - backedBy(backpack.getContents()) - or the region has "
                    + "nowhere to read from and nowhere to write back to.");
        }
        this.backing = store;
        return this;
    }

    @Nonnull
    public StorageBinding policy(@Nonnull ClickPolicy policy) {
        this.policy = policy == null ? ClickPolicy.DENY_ALL : policy;
        return this;
    }

    /** Refuses the items {@code filter} answers true for; filters add up. */
    @Nonnull
    public StorageBinding denyPlace(@Nonnull Predicate<ItemSpec> filter) {
        requireFilter(filter);
        return alsoRequire(filter.negate());
    }

    /** Lets in only the items {@code filter} answers true for; filters add up. */
    @Nonnull
    public StorageBinding allowPlace(@Nonnull Predicate<ItemSpec> filter) {
        requireFilter(filter);
        return alsoRequire(filter);
    }

    /** Runs after the area's contents changed and were written back to the store, on the renderer thread. */
    @Nonnull
    public StorageBinding onChange(@Nullable Consumer<StorageChange> onChange) {
        this.onChange = onChange;
        return this;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public SlotSet getSlots() {
        return slots;
    }

    @Nullable
    public Object getBacking() {
        return backing;
    }

    @Nonnull
    public ClickPolicy getPolicy() {
        return policy;
    }

    @Nullable
    public Consumer<StorageChange> getOnChange() {
        return onChange;
    }

    @Nullable
    public Predicate<ItemSpec> getPlaceFilter() {
        return placeFilter;
    }

    @Override
    public String toString() {
        return "StorageBinding{" + name + ", slots=" + slots + "}";
    }

    private StorageBinding alsoRequire(Predicate<ItemSpec> filter) {
        this.placeFilter = this.placeFilter == null ? filter : this.placeFilter.and(filter);
        return this;
    }

    private void requireFilter(Predicate<ItemSpec> filter) {
        if (filter == null) {
            throw new IllegalArgumentException("The storage region [" + name + "] was given a null item filter. Drop the "
                    + "call to let every item in, and keep the gesture rules in policy(...).");
        }
    }

}
