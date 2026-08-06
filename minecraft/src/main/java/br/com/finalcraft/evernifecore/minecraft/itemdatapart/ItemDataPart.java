package br.com.finalcraft.evernifecore.minecraft.itemdatapart;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * One item concept, described once, in both directions.
 *
 * <p>The pure pair is the round-trip law: {@code parse(format(v)).equals(v)} for every v the part
 * can hold - provable on a bare JVM. The server pair is the application law:
 * {@code extract(apply(v, item))} answers v on a runtime that satisfies the requirement.</p>
 *
 * @param <V> what this concept is, as a value with a decent {@code equals}
 */
public abstract class ItemDataPart<V> {

    public static final int
            PRIORITY_MOST_EARLY = 0,
            PRIORITY_EARLY = 10,
            PRIORITY_NORMAL = 50,
            PRIORITY_LATE = 80,
            PRIORITY_VERY_LATE = 100;

    /** The ONE spelling read emits. Aliases are absorbed on parse and never emitted. */
    @Nonnull
    public abstract String getCanonicalKey();

    /**
     * Text -&gt; value. PURE: no Bukkit, no version checks - runs on any JVM. A bad argument throws
     * {@link ItemLineException} whose message teaches the fix, not just the defect.
     *
     * <p>The argument arrives trimmed, so leading and trailing spaces are never part of a value.</p>
     */
    @Nonnull
    public abstract V parse(@Nonnull String argument) throws ItemLineException;

    /**
     * Value -&gt; the argument side of the line(s). PURE, inverse of parse. Multi-line parts
     * (lore, enchant) emit one argument per entry, and the law folds them back with {@link #merge}.
     */
    @Nonnull
    public abstract List<String> format(@Nonnull V value);

    /** Two lines of the same key in one block: how they combine. Default: last wins. */
    @Nonnull
    public V merge(@Nonnull V previous, @Nonnull V next) {
        return next;
    }

    /**
     * Value -&gt; item. Only called on a runtime satisfying the registration's requirement -
     * version checks do NOT belong in here anymore.
     */
    @Nonnull
    public abstract ItemStack apply(@Nonnull V value, @Nonnull ItemStack item);

    /**
     * Item -&gt; value, or null when the item simply does not carry this concept.
     * "Cannot answer on this runtime" is NOT null - that is the registry's refusal, decided
     * before this method is ever reached.
     */
    @Nullable
    public abstract V extract(@Nonnull ItemStack item);

    /** One definition of similarity, derived from the same V both directions use. */
    public boolean matches(@Nonnull ItemStack base, @Nonnull ItemStack other) {
        return Objects.equals(extract(base), extract(other));
    }

    /** Parts with a lower priority are applied before parts with a higher one. */
    public abstract int getPriority();

}
