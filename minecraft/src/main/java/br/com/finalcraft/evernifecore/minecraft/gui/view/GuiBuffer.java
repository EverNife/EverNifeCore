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
 */
public final class GuiBuffer {

    private final int size;
    private final TreeMap<Integer, ItemStack[]> layers = new TreeMap<>();
    private final ItemStack[] committed;
    private final BitSet dirty;

    public GuiBuffer(int size) {
        this.size = size;
        this.committed = new ItemStack[size];
        this.dirty = new BitSet(size);
    }

    public int getSize() {
        return size;
    }

    /** Paints {@code item} at {@code slot} on {@code layer}. A {@code null} item erases that layer only. */
    public void write(int layer, int slot, ItemStack item) {
        requireInside(slot);
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

    /** Marks every slot for re-comparison, which is how a resync recovers a container someone else touched. */
    public void markAllDirty() {
        dirty.set(0, size);
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
