package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * The screen as the framework believes it looks, one layer per depth, plus the last thing actually written to the
 * surface.
 *
 * <ul>
 *   <li><b>Depth.</b> Every write names a layer. What a slot shows is the topmost layer that has something there, so
 *       erasing the content layer uncovers the background instead of blanking the slot.</li>
 *   <li><b>Diff.</b> {@link #commit()} compares the rendered result against what was last written and answers only the
 *       slots that differ. The key of the comparison is the rendered output ({@link RenderedIcon} is a value), never the
 *       identity of whatever produced it, so an object mutated in place still repaints and an unchanged list costs zero
 *       writes.</li>
 * </ul>
 *
 * <p>A slot the buffer {@link #disown(int) disowns} is outside both: it belongs to the player, and the buffer neither
 * writes it nor claims to know what is in it.</p>
 */
public final class SlotBuffer {

    /** Where a background writes. Anything painted over it uses a higher layer. */
    public static final int LAYER_BACKGROUND = 0;
    /** Where an ordinary icon writes. */
    public static final int LAYER_CONTENT = 100;

    private final int size;
    private final TreeMap<Integer, RenderedIcon[]> layers = new TreeMap<>();
    private final RenderedIcon[] committed;
    private final BitSet dirty;
    private final BitSet disowned;

    public SlotBuffer(int size) {
        this.size = size;
        this.committed = new RenderedIcon[size];
        this.dirty = new BitSet(size);
        this.disowned = new BitSet(size);
    }

    public int getSize() {
        return size;
    }

    /** Hands {@code slot} over to the player: from here on nothing paints over it and nothing erases it. */
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

    /** Paints {@code icon} at {@code slot} on {@code layer}. A {@code null} or empty icon erases that layer only. */
    public void write(int layer, int slot, @Nullable RenderedIcon icon) {
        requireInside(slot);
        if (disowned.get(slot)) {
            return;
        }
        RenderedIcon value = icon == null || icon.isEmpty() ? null : icon;
        RenderedIcon[] target = layers.get(layer);
        if (target == null) {
            if (value == null) {
                return;
            }
            target = new RenderedIcon[size];
            layers.put(layer, target);
        }
        target[slot] = value;
        dirty.set(slot);
    }

    /** Erases one layer everywhere. The layers below it come back into view. */
    public void clearLayer(int layer) {
        RenderedIcon[] target = layers.remove(layer);
        if (target == null) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            if (target[slot] != null) {
                dirty.set(slot);
            }
        }
    }

    /** What {@code slot} shows: the topmost layer that has something there, or {@code null}. */
    @Nullable
    public RenderedIcon resolve(int slot) {
        requireInside(slot);
        for (Map.Entry<Integer, RenderedIcon[]> entry : layers.descendingMap().entrySet()) {
            RenderedIcon icon = entry.getValue()[slot];
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    /** The last icon this buffer wrote at {@code slot}. */
    @Nullable
    public RenderedIcon getCommitted(int slot) {
        requireInside(slot);
        return committed[slot];
    }

    public boolean isDirty() {
        return !dirty.isEmpty();
    }

    /**
     * Re-reads the surface: what it actually shows becomes the baseline, so the next commit overwrites every slot that
     * stopped showing what this buffer drew. Comparing against what was <i>written</i> would find nothing wrong.
     */
    public void adoptContainer(IntFunction<RenderedIcon> actual) {
        for (int slot = 0; slot < size; slot++) {
            committed[slot] = actual.apply(slot);
        }
        dirty.set(0, size);
    }

    /** Forgets what was written, because the window it was written into is gone. */
    public void forgetCommitted() {
        for (int slot = 0; slot < size; slot++) {
            committed[slot] = null;
        }
        dirty.set(0, size);
    }

    /**
     * Marks every slot whose rendered output changed as written, and answers them - the caller sends exactly those to
     * the surface.
     */
    public List<Integer> commit() {
        List<Integer> changed = new ArrayList<>();
        for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
            if (disowned.get(slot)) {
                continue;
            }
            RenderedIcon rendered = resolve(slot);
            if (Objects.equals(committed[slot], rendered)) {
                continue;
            }
            committed[slot] = rendered;
            changed.add(slot);
        }
        dirty.clear();
        return changed;
    }

    private void requireInside(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside a screen of " + size + " slots.");
        }
    }

}
