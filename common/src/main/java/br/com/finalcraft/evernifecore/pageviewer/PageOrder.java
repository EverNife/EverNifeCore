package br.com.finalcraft.evernifecore.pageviewer;

import jakarta.annotation.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The order a page's entries are read in: one extracted value per entry, ascending or descending.
 *
 * <p>The direction is the one that was asked for. There is no inversion on top of the comparator and
 * no comparator a page did not ask for: a page with no order shows what its source returned.</p>
 */
public final class PageOrder<OBJ> {

    private final Function<OBJ, ?> extractor;
    private final boolean descending;

    PageOrder(Function<OBJ, ?> extractor, boolean descending) {
        this.extractor = extractor;
        this.descending = descending;
    }

    /** The value this order reads off {@code object}, which is also what {@code ${value}} answers. */
    @Nullable Object valueOf(OBJ object) {
        return extractor.apply(object);
    }

    /**
     * Sorts entries already carrying their extracted value, so the extractor runs once per entry
     * instead of once per comparison.
     */
    void sort(List<Row<OBJ>> decorated) {
        Comparator<Row<OBJ>> byValue = (left, right) -> compareValues(left.getOrderValue(), right.getOrderValue());
        decorated.sort(descending ? byValue.reversed() : byValue);
    }

    /**
     * Numbers compare numerically whatever boxes they arrived in, two values of the same
     * {@link Comparable} type compare the way that type says, and anything else compares as text.
     * A null sorts first ascending, which is the same place natural ordering would put "nothing".
     */
    @SuppressWarnings("unchecked")
    private static int compareValues(@Nullable Object left, @Nullable Object right) {
        if (left == right) return 0;
        if (left == null) return -1;
        if (right == null) return 1;

        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof Comparable && left.getClass().isInstance(right)) {
            return ((Comparable<Object>) left).compareTo(right);
        }
        return String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(left), String.valueOf(right));
    }
}
