package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiSurface;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The container a gui writes into, as a test can read it back: the current contents plus the whole
 * history of writes, per slot and in order.
 *
 * <p>The history is what makes the diff assertable. "The state changed and only two slots were
 * written" is a statement about writes, not about contents, and contents alone cannot tell a slot
 * that was rewritten with the same item from one that was left alone.</p>
 *
 * <p>{@link #asInventory()} is the same storage wearing the platform's interface, for the few places
 * that demand one - the server hands out an {@code Inventory} when a window is created, and a click
 * event resolves its raw slot through one. It delegates straight back here, so a write that arrives
 * that way is recorded exactly like a direct one; nothing about a real container is simulated. It is
 * also the answer to {@link #isBackedBy(Inventory)}, which is how an event that names a container
 * finds its way to a screen drawn on this one.</p>
 */
public final class SurfaceDouble implements GuiSurface {

    private final int size;
    private final ItemStack[] contents;
    private final List<Write> writes = new ArrayList<>();

    private Inventory inventoryFace;

    public SurfaceDouble(int size) {
        this.size = size;
        this.contents = new ItemStack[size];
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  GuiSurface
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public ItemStack getItem(int slot) {
        return contents[slot];
    }

    @Override
    public void set(int slot, ItemStack item) {
        contents[slot] = item == null ? null : item.clone();
        writes.add(new Write(slot, contents[slot]));
    }

    @Override
    public void clear(int slot) {
        contents[slot] = null;
        writes.add(new Write(slot, null));
    }

    @Override
    public boolean isBackedBy(Inventory inventory) {
        return inventory != null && inventory == inventoryFace;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading the history
    // -----------------------------------------------------------------------------------------------------------------

    /** Every write since the last {@link #forgetWrites()}, in order. */
    public List<Write> getWrites() {
        return new ArrayList<>(writes);
    }

    public int getWriteCount() {
        return writes.size();
    }

    /** How many times {@code slot} was written. */
    public int getWriteCount(int slot) {
        int count = 0;
        for (Write write : writes) {
            if (write.slot == slot) {
                count++;
            }
        }
        return count;
    }

    /** The slots written at least once, in the order they were first touched. */
    public Set<Integer> getWrittenSlots() {
        Set<Integer> slots = new LinkedHashSet<>();
        for (Write write : writes) {
            slots.add(write.slot);
        }
        return slots;
    }

    /** Drops the history and keeps the contents - the "from here on" of a before/after assertion. */
    public void forgetWrites() {
        writes.clear();
    }

    /** Puts an item in without recording it: what a player left behind, not what the gui drew. */
    public void placeWithoutRecording(int slot, ItemStack item) {
        contents[slot] = item;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The platform's face of the same storage
    // -----------------------------------------------------------------------------------------------------------------

    public Inventory asInventory() {
        if (inventoryFace == null) {
            inventoryFace = Doubles.of(Inventory.class)
                    .on("getSize", args -> size)
                    .on("getItem", args -> getItem((Integer) args[0]))
                    .on("setItem", args -> {
                        set((Integer) args[0], (ItemStack) args[1]);
                        return null;
                    })
                    .on("getContents", args -> contents.clone())
                    .build();
        }
        return inventoryFace;
    }

    /** One write the gui made: which slot, and what landed there ({@code null} for a clear). */
    public static final class Write {

        public final int slot;
        public final ItemStack item;

        Write(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }

        @Override
        public String toString() {
            return "Write{" + slot + " <- " + (item == null ? "empty" : item.getType() + " x" + item.getAmount()) + "}";
        }

    }

}
