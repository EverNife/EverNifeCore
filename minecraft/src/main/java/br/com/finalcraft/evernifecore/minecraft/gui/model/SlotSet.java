package br.com.finalcraft.evernifecore.minecraft.gui.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * An ordered set of raw slot indexes, in the order they were declared and without repetition.
 *
 * <p>A set is either <b>fixed</b> - the indexes are known, which is what a config file ever holds -
 * or <b>relative</b>, a shape ("column 3", "the border") that only becomes indexes once the window
 * it is applied to is measured. {@link #resolve(GuiGeometry)} turns the second into the first;
 * on a fixed set it is the identity.</p>
 *
 * <p>The on-disk form is always {@code "[1,2,3]"}. {@link #parse(String)} also reads a bare scalar
 * ({@code 45}) and an empty list; an empty set means "nowhere", which is a valid way to switch an
 * icon off, never an error.</p>
 */
public final class SlotSet implements Iterable<Integer> {

    public static final SlotSet EMPTY = new SlotSet(new int[0], null);

    private final int[] slots;
    private final Function<GuiGeometry, int[]> shape;

    private SlotSet(int[] slots, Function<GuiGeometry, int[]> shape) {
        this.slots = slots;
        this.shape = shape;
    }

    /**
     * A fixed set of raw (0-based) slots. Repeats collapse and the declaration order is kept.
     *
     * @throws IllegalArgumentException on a negative index
     */
    public static SlotSet of(int... slots) {
        if (slots == null || slots.length == 0) {
            return EMPTY;
        }
        return new SlotSet(distinct(slots), null);
    }

    /** A shape resolved against the window it is applied to. See {@link Slots}. */
    public static SlotSet relative(Function<GuiGeometry, int[]> shape) {
        if (shape == null) {
            throw new IllegalArgumentException("A relative SlotSet needs a shape.");
        }
        return new SlotSet(null, shape);
    }

    /** {@code true} while this set still needs a {@link GuiGeometry} to name its slots. */
    public boolean isRelative() {
        return slots == null;
    }

    /** This set with every index known. Identity when it already was. */
    public SlotSet resolve(GuiGeometry geometry) {
        if (!isRelative()) {
            return this;
        }
        if (geometry == null) {
            throw new IllegalArgumentException("A relative SlotSet cannot be resolved without a GuiGeometry.");
        }
        return new SlotSet(distinct(shape.apply(geometry)), null);
    }

    /** @throws IllegalStateException when the set is still relative */
    public int[] toArray() {
        requireFixed();
        return Arrays.copyOf(slots, slots.length);
    }

    /** @throws IllegalStateException when the set is still relative */
    public int size() {
        requireFixed();
        return slots.length;
    }

    /** @throws IllegalStateException when the set is still relative */
    public boolean isEmpty() {
        return size() == 0;
    }

    /** @throws IllegalStateException when the set is still relative */
    public boolean contains(int slot) {
        requireFixed();
        for (int candidate : slots) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<Integer> iterator() {
        requireFixed();
        return new Iterator<Integer>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < slots.length;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return slots[index++];
            }
        };
    }

    /** The on-disk form: {@code "[1,2,3]"}, {@code "[]"} when empty. */
    public String serialize() {
        requireFixed();
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < slots.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(slots[i]);
        }
        return builder.append(']').toString();
    }

    /**
     * Reads the two textual forms a config may hold: the bracketed list {@code "[1,2,3]"} and a bare
     * scalar {@code "45"}. Blank text and {@code "[]"} both mean the empty set.
     *
     * @throws IllegalArgumentException naming the offending text, so the file can be fixed
     */
    public static SlotSet parse(String text) {
        if (text == null) {
            return EMPTY;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return EMPTY;
        }

        String[] tokens = trimmed.split(",");
        List<Integer> parsed = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String slot = token.trim();
            if (slot.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Integer.valueOf(slot));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("[" + slot + "] is not a slot number, in [" + text
                        + "]. A slot list is written as \"[1,2,3]\", or as a single number for one slot.");
            }
        }

        int[] values = new int[parsed.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = parsed.get(i);
        }
        return of(values);
    }

    private void requireFixed() {
        if (isRelative()) {
            throw new IllegalStateException("This SlotSet is a shape, not a list of slots yet. "
                    + "Call resolve(GuiGeometry) first - the gui does it when the icon is bound.");
        }
    }

    private static int[] distinct(int[] values) {
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(values.length);
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("[" + value + "] is not a slot: raw slots start at 0. "
                        + "Slots.at(row, column) is the 1-based form.");
            }
            unique.add(value);
        }
        int[] result = new int[unique.size()];
        int index = 0;
        for (Integer value : unique) {
            result[index++] = value;
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SlotSet)) {
            return false;
        }
        SlotSet that = (SlotSet) other;
        if (isRelative() || that.isRelative()) {
            return this.shape == that.shape;
        }
        return Arrays.equals(this.slots, that.slots);
    }

    @Override
    public int hashCode() {
        return isRelative() ? System.identityHashCode(shape) : Arrays.hashCode(slots);
    }

    @Override
    public String toString() {
        return isRelative() ? "SlotSet{shape}" : serialize();
    }

}
