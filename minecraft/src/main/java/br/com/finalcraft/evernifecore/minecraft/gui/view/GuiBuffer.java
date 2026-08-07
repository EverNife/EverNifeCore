package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * The screen as the framework believes it looks, one layer per depth, plus the last thing actually
 * written to the container.
 *
 * <p>Two properties come out of keeping both:</p>
 * <ul>
 *   <li><b>Depth.</b> Every write names a layer. What a slot shows is the topmost layer that has
 *       something there, so erasing the content layer uncovers the background instead of blanking
 *       the slot - no write of AIR anywhere.</li>
 *   <li><b>Diff.</b> {@link #commit(GuiSurface)} compares the rendered result against what was last
 *       written and touches only the slots that differ. The key of the comparison is the rendered
 *       output, never the identity of whatever produced it, so an object mutated in place still
 *       repaints and an unchanged list still costs zero writes.</li>
 * </ul>
 *
 * <p>A slot the buffer {@link #disown(int) disowns} is outside both: it belongs to the player, and the
 * buffer neither writes it nor claims to know what is in it.</p>
 */
public final class GuiBuffer {

    private final int size;
    private final TreeMap<Integer, ItemStack[]> layers = new TreeMap<>();
    private final ItemStack[] committed;
    private final BitSet dirty;
    private final BitSet disowned;

    public GuiBuffer(int size) {
        this.size = size;
        this.committed = new ItemStack[size];
        this.dirty = new BitSet(size);
        this.disowned = new BitSet(size);
    }

    public int getSize() {
        return size;
    }

    /**
     * Hands {@code slot} over to the player: from here on nothing paints over it and nothing erases it,
     * whatever a render, a resync or a replaced container would otherwise do.
     *
     * <p>This is what an editable region rests on. A slot the buffer still owned would be blanked the
     * moment the picture it drew and the item the player left there disagreed - which is every moment
     * after the first take.</p>
     */
    public void disown(int slot) {
        requireInside(slot);
        disowned.set(slot);
        dirty.clear(slot);
    }

    /** Whether this buffer is the one that decides what {@code slot} shows. */
    public boolean owns(int slot) {
        requireInside(slot);
        return !disowned.get(slot);
    }

    /** Paints {@code item} at {@code slot} on {@code layer}. A {@code null} item erases that layer only. */
    public void write(int layer, int slot, ItemStack item) {
        requireInside(slot);
        if (disowned.get(slot)) {
            return;
        }
        ItemStack[] target = layers.get(layer);
        if (target == null) {
            if (item == null) {
                return;
            }
            target = new ItemStack[size];
            layers.put(layer, target);
        }
        target[slot] = item;
        dirty.set(slot);
    }

    /** Erases one layer everywhere. The layers below it come back into view. */
    public void clearLayer(int layer) {
        ItemStack[] target = layers.remove(layer);
        if (target == null) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            if (target[slot] != null) {
                dirty.set(slot);
            }
        }
    }

    /** Erases one layer at one slot. */
    public void clearLayer(int layer, int slot) {
        requireInside(slot);
        ItemStack[] target = layers.get(layer);
        if (target != null && target[slot] != null) {
            target[slot] = null;
            dirty.set(slot);
        }
    }

    /** What {@code slot} shows: the topmost layer that has something there, or {@code null}. */
    public ItemStack resolve(int slot) {
        requireInside(slot);
        for (Map.Entry<Integer, ItemStack[]> entry : layers.descendingMap().entrySet()) {
            ItemStack item = entry.getValue()[slot];
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    /** The last item this buffer wrote to the container at {@code slot}. */
    public ItemStack getCommitted(int slot) {
        requireInside(slot);
        return committed[slot];
    }

    public boolean isDirty() {
        return !dirty.isEmpty();
    }

    private void markAllDirty() {
        dirty.set(0, size);
    }

    /**
     * Re-reads {@code surface}: what the container actually holds becomes the baseline, so the next
     * commit overwrites every slot that stopped showing what this buffer drew.
     *
     * <p>Comparing against what was <i>written</i> would find nothing wrong - the buffer's memory of
     * a slot does not change when somebody else does - which is exactly the case this exists for.</p>
     */
    public void adoptContainer(GuiSurface surface) {
        for (int slot = 0; slot < size; slot++) {
            ItemStack actual = surface.getItem(slot);
            committed[slot] = actual == null ? null : actual.clone();
        }
        markAllDirty();
    }

    /** Forgets what was written, because the container it was written into is gone (a surface swap). */
    public void forgetCommitted() {
        for (int slot = 0; slot < size; slot++) {
            committed[slot] = null;
        }
        markAllDirty();
    }

    /**
     * Writes the slots whose rendered output changed, and only those.
     *
     * @return how many slots were actually written
     * @throws IllegalStateException off the main thread, naming the thread that tried
     */
    public int commit(GuiSurface surface) {
        if (!FCBukkitUtil.isMainThread()) {
            throw new IllegalStateException("A gui commit was attempted on thread ["
                    + Thread.currentThread().getName() + "]. Every write to an inventory has to happen on "
                    + "the server main thread - hop with FCScheduler/McFCScheduler before touching the gui.");
        }

        int writes = 0;
        for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
            if (disowned.get(slot)) {
                continue;
            }
            ItemStack rendered = resolve(slot);
            if (isSameOutput(committed[slot], rendered)) {
                continue;
            }
            if (rendered == null) {
                surface.clear(slot);
            } else {
                surface.set(slot, rendered);
            }
            committed[slot] = rendered == null ? null : rendered.clone();
            writes++;
        }
        dirty.clear();
        return writes;
    }

    /**
     * Whether two rendered slots look the same to a player. {@code isSimilar} deliberately ignores
     * the amount, so the amount is compared here - a counter that only changes the stack size still
     * has to repaint.
     */
    public static boolean isSameOutput(ItemStack current, ItemStack rendered) {
        if (isEmpty(current)) {
            return isEmpty(rendered);
        }
        if (isEmpty(rendered)) {
            return false;
        }
        return current.getAmount() == rendered.getAmount() && current.isSimilar(rendered);
    }

    /** A container answers an empty slot as {@code null} on some versions and as AIR on others. */
    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private void requireInside(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside a gui of " + size + " slots.");
        }
    }

}
