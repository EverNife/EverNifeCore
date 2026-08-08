package br.com.finalcraft.evernifecore.pageviewer;

import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One entry of a {@link PageSnapshot}: the object, the value it was ordered by, its 1-based position
 * and what this page's {@code row} keys have already answered for it.
 *
 * <p>A {@code row} key is computed on first mention and remembered here, so a page a hundred players
 * are reading still costs one call into the caller's function per line - which is the whole reason
 * the level has a name of its own.</p>
 */
public final class Row<OBJ> {

    private final OBJ object;
    private final @Nullable Object orderValue;
    private final int position;

    // Reached from every render of this row, and two recipients may be served from different
    // threads, so the memo has to stand on its own.
    private final Map<Object, Optional<Object>> resolved = new ConcurrentHashMap<>();

    Row(OBJ object, @Nullable Object orderValue, int position) {
        this.object = object;
        this.orderValue = orderValue;
        this.position = position;
    }

    public OBJ getObject() {
        return object;
    }

    /** What {@code orderBy} extracted for this entry, or {@code null} on a page that declared no order. */
    public @Nullable Object getOrderValue() {
        return orderValue;
    }

    /** 1-based position within the snapshot - what {@code ${number}} answers. */
    public int getPosition() {
        return position;
    }

    /**
     * The value of one declaration for this entry, computed at most once. {@code declaration} is that
     * declaration's own identity, so the same key declared by two pages keeps two answers.
     *
     * <p>Resolving to {@code null} is itself an answer worth remembering - otherwise the key would be
     * recomputed on every mention - which is what the {@link Optional} carrier is for.</p>
     */
    @Nullable Object resolveOnce(Object declaration, Supplier<?> compute) {
        return resolved.computeIfAbsent(declaration, ignored -> Optional.ofNullable(compute.get()))
                .orElse(null);
    }

    /** The same entry at another position, which is what numbering an ordered list produces. */
    Row<OBJ> at(int position) {
        return new Row<>(object, orderValue, position);
    }
}
