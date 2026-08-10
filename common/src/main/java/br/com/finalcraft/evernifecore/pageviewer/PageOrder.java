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
     * Numbers compare numerically whatever boxes they arrived in, constants of one enum compare in
     * the order they were declared in, two values of the very same {@link Comparable} type compare
     * the way that type says, and anything else compares as text - case-insensitively, in every
     * branch that reaches text, so "Zoe" and "alice" land where a reader expects them.
     *
     * <p>Every branch answers the same for both operands or is not taken at all. A comparison that
     * holds one way and not the other is what a sort of more than a handful of entries reports as
     * "Comparison method violates its general contract!" - and, below that size, as an order nobody
     * asked for.</p>
     *
     * <p>A null sorts first ascending, which is the same place natural ordering would put
     * "nothing".</p>
     */
    @SuppressWarnings("unchecked")
    private static int compareValues(@Nullable Object left, @Nullable Object right) {
        if (left == right) return 0;
        if (left == null) return -1;
        if (right == null) return 1;

        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof CharSequence && right instanceof CharSequence) {
            return String.CASE_INSENSITIVE_ORDER.compare(left.toString(), right.toString());
        }
        //asked of the declaring enum and not of the value's own class: a constant with a body is a
        //subclass of its enum, so two of them are two different classes carrying one order
        if (left instanceof Enum && right instanceof Enum
                && ((Enum<?>) left).getDeclaringClass() == ((Enum<?>) right).getDeclaringClass()) {
            return Integer.compare(((Enum<?>) left).ordinal(), ((Enum<?>) right).ordinal());
        }
        if (left instanceof Comparable && left.getClass() == right.getClass()) {
            return ((Comparable<Object>) left).compareTo(right);
        }
        return String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(left), String.valueOf(right));
    }
}
