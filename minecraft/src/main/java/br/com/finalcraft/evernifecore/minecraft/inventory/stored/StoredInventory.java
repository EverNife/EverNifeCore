package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Storage a player may really take from and fill: a number of slots it knows, a maximum stack size per
 * slot, handlers that see a change coming and may refuse it, and a serialized form that carries the
 * number of the schema it was written with.
 *
 * <pre>{@code
 * StoredInventory backpack = new StoredInventory(27);
 * backpack.setMaxStackSize(0, 1);                       // a slot that holds one of anything
 * backpack.onPreUpdate(event -> event.setCancelled(locked));
 * backpack.onPostUpdate(event -> owner.markDirty());
 *
 * storage(l -> l.AREA).backedBy(backpack).policy(ClickPolicy.EDIT_ALL);
 * }</pre>
 *
 * <h2>The thread contract</h2>
 * <b>Every change happens on the server main thread.</b> A change attempted from anywhere else throws
 * instead of landing, naming the thread that tried: an inventory written from two threads is a
 * duplicated item, and a duplicated item found weeks later is unattributable. <b>Reading is allowed
 * from any thread</b> and answers a snapshot: a slot is written and copied under the same lock, so a
 * reader gets the whole stack before a change or the whole stack after it, never a stack being edited.
 * What a reader cannot have is a promise that the snapshot is still true by the time it is used -
 * nothing outside the main thread can have that, and a lock held across a decision would only move the
 * race somewhere harder to see.
 *
 * <h2>The stored form</h2>
 * It persists as an envelope carrying its schema version, so a file written today is still readable
 * after the shape changes - see {@link StoredInventorySchema}. The oldest shape it reads is a bare
 * {@link GenericInventory} slot map, which is what every inventory in this project was written as
 * before this type existed.
 *
 * <p>{@link GenericInventory} itself is untouched by all of this. It is mirrored on another platform
 * and composed into player data all over the place; what it does not have is added around it, never to
 * it.</p>
 */
public final class StoredInventory implements ItemStore {

    /** The maximum stack size of a slot nobody gave one: whatever the item in it says. */
    public static final int ITEM_DEFAULT = 0;

    /** Guarded by {@code this}: every change takes the monitor, and so does every copy handed out. */
    private ItemStack[] items;
    private int[] maxStackSizes;

    private Consumer<StoredInventoryItemPreUpdateEvent> preUpdate;
    private Consumer<StoredInventoryItemPostUpdateEvent> postUpdate;

    public StoredInventory(int size) {
        requireSize(size);
        this.items = new ItemStack[size];
        this.maxStackSizes = new int[size];
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading - allowed from any thread, answered as a snapshot
    // -----------------------------------------------------------------------------------------------------------------

    /** How many slots there are. Same as {@link #getCapacity()}, which is the name the seam uses. */
    public synchronized int getSize() {
        return items.length;
    }

    @Override
    public synchronized int getCapacity() {
        return items.length;
    }

    @Override
    @Nullable
    public synchronized ItemStack getItem(int slot) {
        return isInside(slot) ? copyOf(items[slot]) : null;
    }

    /** Every slot, in order, empty ones as {@code null}. Copies, so writing into it reaches nothing. */
    @Nonnull
    public synchronized List<ItemStack> getContents() {
        List<ItemStack> contents = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            contents.add(copyOf(item));
        }
        return contents;
    }

    public synchronized boolean isEmpty() {
        for (ItemStack item : items) {
            if (!isVoid(item)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nonnull
    public synchronized int[] getOccupiedSlots() {
        int[] occupied = new int[items.length];
        int size = 0;
        for (int slot = 0; slot < items.length; slot++) {
            if (!isVoid(items[slot])) {
                occupied[size++] = slot;
            }
        }
        return Arrays.copyOf(occupied, size);
    }

    /**
     * The maximum stack size declared for {@code slot}, or {@link #ITEM_DEFAULT} when the item in it
     * decides.
     */
    public synchronized int getMaxStackSize(int slot) {
        return isInside(slot) ? maxStackSizes[slot] : ITEM_DEFAULT;
    }

    @Override
    public synchronized int getMaxStackSize(int slot, @Nullable ItemStack item) {
        int declared = isInside(slot) ? maxStackSizes[slot] : ITEM_DEFAULT;
        int natural = ItemStore.maxStackSizeOf(item);
        return declared == ITEM_DEFAULT ? natural : Math.min(declared, natural);
    }

    /**
     * True when this inventory has something to say about a change before it happens: a pre-update
     * handler that may refuse it, or a slot that holds less than the item in it would.
     */
    @Override
    public synchronized boolean vetsUpdates() {
        if (preUpdate != null) {
            return true;
        }
        for (int max : maxStackSizes) {
            if (max != ITEM_DEFAULT) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declaring
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Caps how much one slot holds, whatever the item in it would allow. {@link #ITEM_DEFAULT} gives the
     * slot back to the item.
     *
     * <p>The cap is what the framework fills a slot UP TO - a shift-click or a drag stops there and the
     * rest stays where it was. It is not a wall the world is measured against: an item already in the
     * slot is never cut down to size, because cutting it down is destroying it.</p>
     */
    @Nonnull
    public synchronized StoredInventory setMaxStackSize(int slot, int max) {
        requireInside(slot, "give a maximum stack size to");
        if (max < ITEM_DEFAULT) {
            throw new IllegalArgumentException("A slot cannot hold " + max + " items. Give slot " + slot
                    + " a maximum of at least 1, or StoredInventory.ITEM_DEFAULT to let the item in it decide.");
        }
        maxStackSizes[slot] = max;
        return this;
    }

    /**
     * The handler asked before every change, which may refuse it. One handler: declaring another
     * replaces it.
     */
    @Nonnull
    public StoredInventory onPreUpdate(@Nullable Consumer<StoredInventoryItemPreUpdateEvent> handler) {
        this.preUpdate = handler;
        return this;
    }

    /** The handler told after every change. One handler: declaring another replaces it. */
    @Nonnull
    public StoredInventory onPostUpdate(@Nullable Consumer<StoredInventoryItemPostUpdateEvent> handler) {
        this.postUpdate = handler;
        return this;
    }

    /**
     * Grows or shrinks the inventory. Growing is always allowed; shrinking is refused when it would drop
     * an item, naming the slot that holds it.
     */
    @Nonnull
    public StoredInventory setCapacity(int capacity) {
        requireMainThread("resize");
        requireSize(capacity);
        synchronized (this) {
            for (int slot = capacity; slot < items.length; slot++) {
                if (!isVoid(items[slot])) {
                    throw new IllegalStateException("This inventory cannot shrink to " + capacity
                            + " slots: slot " + slot + " still holds " + items[slot].getType() + ". Take the "
                            + "items past slot " + (capacity - 1) + " out first - shrinking over them would "
                            + "destroy them.");
                }
            }
            this.items = Arrays.copyOf(items, capacity);
            this.maxStackSizes = Arrays.copyOf(maxStackSizes, capacity);
        }
        return this;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Changing - main thread only
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Changes {@code slot}: asks the pre-update handler, writes when it agrees, and tells the post-update
     * handler afterwards.
     *
     * @return whether the change happened - {@code false} means a handler refused it
     * @throws IllegalStateException off the main thread, or when {@code item} is a bigger stack than the
     *                               slot's maximum
     */
    public boolean setItem(@Nonnull UpdateCause cause, int slot, @Nullable ItemStack item) {
        requireMainThread("change");
        requireInside(slot, "change");
        ItemStack previous = getItem(slot);
        requireFits(slot, item);
        if (!mayUpdate(cause, slot, previous, item)) {
            return false;
        }
        writeSlot(slot, item);
        reportUpdate(cause, slot, previous, item);
        return true;
    }

    /**
     * Writes {@code slot} without asking and without telling.
     *
     * <p>Nothing is measured against the slot's maximum here: this is the door the framework puts back
     * what a container already holds through, and refusing what is already in the world would destroy
     * it.</p>
     */
    @Override
    public void setItemSilently(int slot, @Nullable ItemStack item) {
        requireMainThread("change");
        requireInside(slot, "change");
        writeSlot(slot, item);
    }

    @Override
    public boolean mayUpdate(@Nonnull UpdateCause cause, int slot, @Nullable ItemStack previous,
                             @Nullable ItemStack next) {
        Consumer<StoredInventoryItemPreUpdateEvent> handler = this.preUpdate;
        if (handler == null) {
            return true;
        }
        StoredInventoryItemPreUpdateEvent event = new StoredInventoryItemPreUpdateEvent(this, cause, slot, previous, next);
        try {
            handler.accept(event);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onPreUpdate handler of a stored inventory failed for slot "
                    + slot + ", so the change was refused: " + e);
            e.printStackTrace();
            return false;
        }
        return !event.isCancelled();
    }

    @Override
    public void reportUpdate(@Nonnull UpdateCause cause, int slot, @Nullable ItemStack previous,
                             @Nullable ItemStack next) {
        Consumer<StoredInventoryItemPostUpdateEvent> handler = this.postUpdate;
        if (handler == null) {
            return;
        }
        try {
            handler.accept(new StoredInventoryItemPostUpdateEvent(this, cause, slot, previous, next));
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onPostUpdate handler of a stored inventory failed for slot "
                    + slot + ": " + e);
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "StoredInventory{" + getOccupiedSlots().length + "/" + getSize() + " slots filled}";
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Internals
    // -----------------------------------------------------------------------------------------------------------------

    private synchronized void writeSlot(int slot, ItemStack item) {
        items[slot] = copyOf(item);
    }

    private synchronized boolean isInside(int slot) {
        return slot >= 0 && slot < items.length;
    }

    private void requireInside(int slot, String action) {
        if (!isInside(slot)) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside a stored inventory of "
                    + getSize() + " slots, so there is nothing to " + action + " there. Slots count from 0, "
                    + "and setCapacity(int) is how one gets more of them.");
        }
    }

    private void requireFits(int slot, ItemStack item) {
        if (isVoid(item)) {
            return;
        }
        int max = getMaxStackSize(slot, item);
        if (item.getAmount() > max) {
            throw new IllegalStateException("A stack of " + item.getAmount() + " " + item.getType()
                    + " does not fit slot " + slot + ", which holds " + max + ". Split it, or raise the slot's "
                    + "maximum with setMaxStackSize(" + slot + ", n).");
        }
    }

    private static void requireSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("A stored inventory of " + size + " slots holds nothing and "
                    + "can never hold anything. Give it the number of slots the region showing it has.");
        }
    }

    /**
     * Refuses a change made from anywhere but the main thread. A server that is not up yet answers
     * nothing, and then there is no other thread to be on.
     */
    private static void requireMainThread(String action) {
        Server server = Bukkit.getServer();
        if (server != null && !server.isPrimaryThread()) {
            throw new IllegalStateException("A stored inventory was asked to " + action + " from thread ["
                    + Thread.currentThread().getName() + "]. Items are moved on the server main thread and "
                    + "nowhere else - hop with FCScheduler/McFCScheduler and change it there. Reading it from "
                    + "here is fine: getItem and getContents answer a snapshot from any thread.");
        }
    }

    private static ItemStack copyOf(ItemStack item) {
        return isVoid(item) ? null : item.clone();
    }

    private static boolean isVoid(ItemStack item) {
        return item == null || item.getAmount() <= 0 || item.getType() == Material.AIR;
    }

}
