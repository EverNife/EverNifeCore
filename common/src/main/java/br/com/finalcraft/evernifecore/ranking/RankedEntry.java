package br.com.finalcraft.evernifecore.ranking;

import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One entry of a {@link RankedSnapshot}: the object, the value it was ordered by, its 1-based position and what the
 * list's declared values have already answered for it.
 *
 * <p>A declared value is computed on first mention and remembered here, so a list a hundred players are reading still
 * costs one call into the owner's function per entry.</p>
 */
public final class RankedEntry<T> {

    private final T object;
    private final @Nullable Object orderValue;
    private final int position;

    // Reached from every reader of this entry, and two readers may be served from different threads.
    private final Map<Object, Optional<Object>> resolved = new ConcurrentHashMap<>();

    RankedEntry(T object, @Nullable Object orderValue, int position) {
        this.object = object;
        this.orderValue = orderValue;
        this.position = position;
    }

    public T getObject() {
        return object;
    }

    /** What {@code orderBy} extracted for this entry, or {@code null} on a list that declared no order. */
    public @Nullable Object getOrderValue() {
        return orderValue;
    }

    /** 1-based position within the snapshot - what {@code ${number}} answers. */
    public int getPosition() {
        return position;
    }

    /**
     * The value of one declaration for this entry, computed at most once; {@code declaration} is that declaration's own
     * identity. A {@code null} answer is remembered too, which is what the {@link Optional} carrier is for.
     */
    public @Nullable Object resolveOnce(Object declaration, Supplier<?> compute) {
        return resolved.computeIfAbsent(declaration, ignored -> Optional.ofNullable(compute.get())).orElse(null);
    }

    RankedEntry<T> at(int newPosition) {
        return new RankedEntry<>(object, orderValue, newPosition);
    }
}
