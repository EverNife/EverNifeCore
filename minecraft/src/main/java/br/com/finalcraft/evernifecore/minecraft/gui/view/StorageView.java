package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.component.StorageBinding;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
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
 */
public final class StorageView {

    /** What a stack is assumed to hold when the server cannot say. Every vanilla stack fits in it. */
    private static final int FALLBACK_MAX_STACK = 64;

    /** Which store each open region is reading and writing, so a second screen on one can be reported. */
    private static final Map<GenericInventory, StorageView> CLAIMED = new IdentityHashMap<>();

    private final GuiView view;
    private final StorageBinding binding;
    private final int[] slots;
    private final Predicate<ItemStack> placeFilter;

    private Cancellable pendingSync;

    StorageView(GuiView view, StorageBinding binding, int[] slots) {
        this.view = view;
        this.binding = binding;
        this.slots = slots;
        this.placeFilter = binding.getPlaceFilter();
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

    @Nonnull
    public ClickPolicy getPolicy() {
        return binding.getPolicy();
    }

    /** Whether {@code item} may enter here - see {@link StorageBinding#denyPlace}. Nothing always may. */
    public boolean mayHold(@Nullable ItemStack item) {
        return item == null || placeFilter == null || placeFilter.test(item);
    }

    /** Whether anything is filtered at all, which is what makes an item nobody can read worth refusing. */
    public boolean hasPlaceFilter() {
        return placeFilter != null;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Filling the window, and putting what is in it back
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Writes the store into a container that has just been made, so only the occupied indexes are
     * touched - a fresh container is already empty.
     */
    void seed(GuiSurface surface) {
        GenericInventory store = binding.getBacking();
        for (int index = 0; index < slots.length; index++) {
            ItemStack stored = store.getItem(index);
            if (!GuiBuffer.isEmpty(stored)) {
                surface.set(slots[index], stored.clone());
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
     * @param last whether the screen is going away, making this the last change it reports
     */
    void syncNow(boolean last) {
        cancelPendingSync();
        GenericInventory store = binding.getBacking();
        GuiSurface surface = view.getSurface();
        List<ItemStack> contents = new ArrayList<>(slots.length);
        for (int index = 0; index < slots.length; index++) {
            ItemStack shown = surface.getItem(slots[index]);
            if (GuiBuffer.isEmpty(shown)) {
                store.removeItem(index);
                contents.add(null);
            } else {
                //two copies: one the store keeps, one the handler may do as it likes with
                store.setItem(index, shown.clone());
                contents.add(shown.clone());
            }
        }
        fire(contents, last);
    }

    /** Gives up the store and stops any read that was still coming. */
    void teardown() {
        cancelPendingSync();
        unclaim(binding.getBacking());
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
                    + "] failed for [" + view.getViewerName() + "]: " + e);
            e.printStackTrace();
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
     * Adds up to {@code amount} of {@code source} at one of this region's slots.
     *
     * @return how much actually went in, which is what the caller has to take off the source
     */
    int addAt(int rawSlot, ItemStack source, int amount) {
        if (amount <= 0 || GuiBuffer.isEmpty(source) || !mayHold(source)) {
            return 0;
        }
        GuiSurface surface = view.getSurface();
        ItemStack current = surface.getItem(rawSlot);
        int max = maxStackSizeOf(source);
        if (GuiBuffer.isEmpty(current)) {
            int given = Math.min(amount, max);
            ItemStack put = source.clone();
            put.setAmount(given);
            surface.set(rawSlot, put);
            return given;
        }
        if (!current.isSimilar(source)) {
            return 0;
        }
        int given = Math.min(amount, max - current.getAmount());
        if (given <= 0) {
            return 0;
        }
        ItemStack merged = current.clone();
        merged.setAmount(current.getAmount() + given);
        surface.set(rawSlot, merged);
        return given;
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

    /** What one stack of {@code item} holds on this server, or {@link #FALLBACK_MAX_STACK} when it will not say. */
    static int maxStackSizeOf(ItemStack item) {
        try {
            int max = item.getMaxStackSize();
            if (max > 0) {
                return max;
            }
        } catch (Throwable unanswerable) {
            //an item type this server cannot measure still has to be movable
        }
        return FALLBACK_MAX_STACK;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the plugin has to be told about
    // -----------------------------------------------------------------------------------------------------------------

    private void reportItemsPastCapacity(GenericInventory store) {
        int hidden = 0;
        for (ItemInSlot itemInSlot : store.getItems()) {
            if (itemInSlot.getSlot() >= slots.length) {
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

    private void claim(GenericInventory store) {
        StorageView previous = CLAIMED.put(store, this);
        if (previous != null && previous != this) {
            EverNifeCore.getLog().warning("The storage region [" + binding.getName() + "] opened for ["
                    + view.getViewerName() + "] reads the same store ["
                    + previous.getView().getViewerName() + "] already has open. Each screen holds its own copy "
                    + "of it and the last one to close is the one that wins, so the other's items are lost. "
                    + "Open one screen on a store at a time.");
        }
    }

    private void unclaim(GenericInventory store) {
        if (store != null && CLAIMED.get(store) == this) {
            CLAIMED.remove(store);
        }
    }

}
