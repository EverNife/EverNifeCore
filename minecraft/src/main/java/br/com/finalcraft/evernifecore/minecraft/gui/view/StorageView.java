package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.component.StorageBinding;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One viewer's editable region: the slots it holds inside their container, the store behind it, and the
 * bookkeeping that keeps the two in step.
 *
 * <p>While the screen is open the container is the source of truth. The framework fills it once from
 * the store, then stops writing those slots at all - {@link GuiBuffer#disown(int)} - and reads them back
 * into the store on the tick after every accepted change. That is the whole safety argument of an
 * editable area: the player and the framework never write the same slot in the same tick.</p>
 *
 * <p>A store that {@link ItemStore#vetsUpdates() vets its updates} is asked one step earlier than that,
 * while the click is still cancellable and nothing has moved. Reading the result back afterwards would
 * be too late to refuse anything, which is why the framework models what the gesture is about to do
 * instead of waiting to see it.</p>
 */
public final class StorageView {

    /** Which store each open region is reading and writing, so a second screen on one can be reported. */
    private static final Map<Object, StorageView> CLAIMED = new IdentityHashMap<>();

    private final GuiView view;
    private final StorageBinding binding;
    private final int[] slots;
    //a binding is shared by every viewer of the screen and stays writable after one of them opened it:
    //what a click is judged by has to be what was there when the window was filled, or a region would
    //seed one store and write back into another
    private final ItemStore store;
    private final ClickPolicy policy;
    private final Predicate<ItemStack> placeFilter;

    private Cancellable pendingSync;

    StorageView(GuiView view, StorageBinding binding, int[] slots) {
        this.view = view;
        this.binding = binding;
        this.slots = slots;
        this.store = requireStore(view, binding);
        this.policy = binding.getPolicy();
        this.placeFilter = binding.getPlaceFilter();
    }

    private static ItemStore requireStore(GuiView view, StorageBinding binding) {
        ItemStore backing = binding.getBacking();
        if (backing == null) {
            throw new IllegalStateException("The storage region [" + binding.getName() + "] of ["
                    + view.getGui().getTitle() + "] has no store to keep its contents in: call backedBy(...) "
                    + "on it with the GenericInventory the plugin persists. A region without one would open "
                    + "empty and throw away whatever the player put in it.");
        }
        return backing;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public StorageBinding getBinding() {
        return binding;
    }

    @Nonnull
    public GuiView getView() {
        return view;
    }

    /** What gestures go through here, as the binding declared them when this window was filled. */
    @Nonnull
    public ClickPolicy getPolicy() {
        return policy;
    }

    /** The store this region reads and writes - the one it was filled from, for as long as it is open. */
    @Nonnull
    public ItemStore getStore() {
        return store;
    }

    /** Whether {@code item} may enter here - see {@link StorageBinding#denyPlace}. Nothing always may. */
    public boolean mayHold(@Nullable ItemStack item) {
        return item == null || placeFilter == null || placeFilter.test(item);
    }

    /** Whether anything is filtered at all, which is what makes an item nobody can read worth refusing. */
    public boolean hasPlaceFilter() {
        return placeFilter != null;
    }

    /** Whether the store behind this region judges a change before it happens. */
    public boolean vetsUpdates() {
        return store.vetsUpdates();
    }

    /** This region's own index for a raw slot of the window, or {@code -1} when it holds no such slot. */
    public int indexOf(int rawSlot) {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Whether {@code rawSlot} may become {@code next}: it has to fit the slot, and then the store has to
     * agree. The slot's current content is what the container shows, which is what the player is acting
     * on.
     *
     * <p>A stack too big for the slot is refused without asking - there is nothing to decide about a
     * gesture that cannot happen, and the pre-update handler is for decisions. What "too big" means is
     * only ever about GROWING what is already there: a slot that holds more than it should - filled
     * before anybody said how much it takes - can still be emptied, halved and taken from, or the items
     * in it would be stranded there by the very rule meant to limit them. Putting something ELSE in it
     * is not that: a different item is a new stack, and a new stack has to fit.</p>
     */
    public boolean mayAccept(int rawSlot, @Nullable ItemStack next) {
        int index = indexOf(rawSlot);
        if (index < 0) {
            return true;
        }
        ItemStack shown = view.getSurface().getItem(rawSlot);
        if (!GuiBuffer.isEmpty(next) && next.getAmount() > store.getMaxStackSize(index, next)
                && !(next.isSimilar(shown) && next.getAmount() <= shown.getAmount())) {
            return false;
        }
        return store.mayUpdate(UpdateCause.PLAYER, index, shown, next);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Filling the window, and putting what is in it back
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Writes the store into a container that has just been made, so only the occupied indexes are
     * touched - a fresh container is already empty.
     */
    void seed(GuiSurface surface) {
        requireRoomForTheRegion(store);
        for (int index = 0; index < slots.length; index++) {
            ItemStack stored = store.getItem(index);
            if (!GuiBuffer.isEmpty(stored)) {
                surface.set(slots[index], stored);
            }
        }
        reportItemsPastCapacity(store);
        claim(store);
    }

    /**
     * Moves the region across to a replacement container. A title change and a screen coming back from
     * underneath another both build a new one, and the items are in the old one.
     */
    void carryOver(GuiSurface from, GuiSurface to) {
        for (int slot : slots) {
            ItemStack held = from.getItem(slot);
            if (!GuiBuffer.isEmpty(held)) {
                to.set(slot, held.clone());
            }
        }
    }

    /**
     * Reads the region back into the store and tells the plugin, on the tick after the change: the
     * server applies a click once every listener has had its say, so what the slot holds is only
     * knowable next tick. Several changes in one tick cost one read.
     */
    void scheduleSync() {
        if (pendingSync != null || view.isClosed()) {
            return;
        }
        pendingSync = view.getScheduler().later(1L, () -> {
            pendingSync = null;
            if (!view.isClosed()) {
                syncNow(false);
            }
        });
    }

    /**
     * Reads the region back into the store right now.
     *
     * <p>The store is written first and told afterwards: a post-update handler that reads the inventory
     * it was called about has to find the change already in it, and one that reads a neighbouring slot
     * has to find that one settled too.</p>
     *
     * @param last whether the screen is going away, making this the last change it reports
     */
    void syncNow(boolean last) {
        cancelPendingSync();
        GuiSurface surface = view.getSurface();
        List<ItemStack> contents = new ArrayList<>(slots.length);
        List<ItemStack> replaced = new ArrayList<>(slots.length);
        for (int index = 0; index < slots.length; index++) {
            ItemStack shown = surface.getItem(slots[index]);
            replaced.add(store.getItem(index));
            //two copies: one the store keeps, one the handler may do as it likes with
            store.setItemSilently(index, GuiBuffer.isEmpty(shown) ? null : shown.clone());
            contents.add(GuiBuffer.isEmpty(shown) ? null : shown.clone());
        }
        for (int index = 0; index < slots.length; index++) {
            if (!GuiBuffer.isSameOutput(replaced.get(index), contents.get(index))) {
                store.reportUpdate(UpdateCause.PLAYER, index, replaced.get(index), contents.get(index));
            }
        }
        fire(contents, last);
    }

    /** Gives up the store and stops any read that was still coming. */
    void teardown() {
        cancelPendingSync();
        unclaim(store);
    }

    private void fire(List<ItemStack> contents, boolean last) {
        Consumer<StorageContext> handler = binding.getOnChange();
        if (handler == null) {
            return;
        }
        try {
            handler.accept(new StorageContext(this, contents, last));
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onChange handler of the storage region [" + binding.getName()
                    + "] failed for [" + view.getViewerName() + "]", e);
        }
    }

    private void cancelPendingSync() {
        if (pendingSync != null) {
            pendingSync.cancel();
            pendingSync = null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Writing into the region - the gestures the framework has to carry out itself
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Adds up to {@code amount} of {@code source} at one of this region's slots, as far as the slot's own
     * maximum allows and as far as the store agrees.
     *
     * @return how much actually went in, which is what the caller has to take off the source
     */
    int addAt(int rawSlot, ItemStack source, int amount) {
        int index = indexOf(rawSlot);
        if (index < 0 || amount <= 0 || GuiBuffer.isEmpty(source) || !mayHold(source)) {
            return 0;
        }
        GuiSurface surface = view.getSurface();
        ItemStack current = surface.getItem(rawSlot);
        int max = store.getMaxStackSize(index, source);
        ItemStack merged;
        if (GuiBuffer.isEmpty(current)) {
            merged = source.clone();
            merged.setAmount(Math.min(amount, max));
        } else {
            if (!current.isSimilar(source)) {
                return 0;
            }
            int given = Math.min(amount, max - current.getAmount());
            if (given <= 0) {
                return 0;
            }
            merged = current.clone();
            merged.setAmount(current.getAmount() + given);
        }
        if (merged.getAmount() <= 0 || !mayAccept(rawSlot, merged)) {
            return 0;
        }
        surface.set(rawSlot, merged);
        return merged.getAmount() - (GuiBuffer.isEmpty(current) ? 0 : current.getAmount());
    }

    /**
     * Spreads up to {@code amount} of {@code source} over the region, topping up the stacks already
     * there before filling an empty slot - the order vanilla itself uses for a shift-click.
     *
     * @return how much actually went in
     */
    int pourIn(ItemStack source, int amount) {
        GuiSurface surface = view.getSurface();
        int placed = 0;
        for (int slot : slots) {
            if (placed >= amount) {
                break;
            }
            if (!GuiBuffer.isEmpty(surface.getItem(slot))) {
                placed += addAt(slot, source, amount - placed);
            }
        }
        for (int slot : slots) {
            if (placed >= amount) {
                break;
            }
            if (GuiBuffer.isEmpty(surface.getItem(slot))) {
                placed += addAt(slot, source, amount - placed);
            }
        }
        return placed;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the plugin has to be told about
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Makes sure the store has room for every slot of the region, which is what says how big this area
     * is: a region wider than its store would write past the end of it the first time a player filled it.
     *
     * <p>An inventory that can be grown simply is. It comes back from an older stored shape as big as
     * the slots that were filled in it, so a backpack of 27 whose owner only ever used ten is read back
     * with ten - and growing it loses nothing, while refusing it makes the screen impossible to open.</p>
     */
    private void requireRoomForTheRegion(ItemStore store) {
        if (store.getCapacity() >= slots.length) {
            return;
        }
        if (store instanceof StoredInventory) {
            //open() runs on the main thread, which is the only place a capacity may change
            ((StoredInventory) store).setCapacity(slots.length);
            return;
        }
        throw new IllegalStateException("The storage region [" + binding.getName() + "] has "
                + slots.length + " slots and its store holds " + store.getCapacity() + ". The region would "
                + "have nowhere to put what a player leaves in the slots past that - grow the store to "
                + slots.length + " slots before the screen opens, on the main thread, or give the region "
                + "fewer slots.");
    }

    private void reportItemsPastCapacity(ItemStore store) {
        int hidden = 0;
        for (int occupied : store.getOccupiedSlots()) {
            if (occupied >= slots.length) {
                hidden++;
            }
        }
        if (hidden > 0) {
            EverNifeCore.getLog().warning("The storage region [" + binding.getName() + "] has "
                    + slots.length + " slots, but its store holds " + hidden + " item(s) past that. They are "
                    + "not shown and are left untouched - give the region as many slots as the store was "
                    + "filled with, or those items stay out of reach.");
        }
    }

    private void claim(ItemStore store) {
        StorageView previous = CLAIMED.put(store.getSharedSource(), this);
        if (previous != null && previous != this) {
            EverNifeCore.getLog().warning("The storage region [" + binding.getName() + "] opened for ["
                    + view.getViewerName() + "] reads the same store ["
                    + previous.getView().getViewerName() + "] already has open. Each screen holds its own copy "
                    + "of it and the last one to close is the one that wins, so the other's items are lost. "
                    + "Open one screen on a store at a time.");
        }
    }

    private void unclaim(ItemStore store) {
        if (store != null && CLAIMED.get(store.getSharedSource()) == this) {
            CLAIMED.remove(store.getSharedSource());
        }
    }

}
