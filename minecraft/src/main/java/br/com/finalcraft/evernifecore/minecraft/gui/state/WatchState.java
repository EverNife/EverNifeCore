package br.com.finalcraft.evernifecore.minecraft.gui.state;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A state read from somewhere else: a domain object that changes on its own, watched instead of
 * mirrored.
 *
 * <p>It does not schedule anything and does not know what a tick is - {@link #poll()} is called from
 * outside, once per tick of an open view, which is what keeps this type free of the platform. Which of
 * those ticks the source is actually read on is this watch's own business: one view drives every watch
 * it has from the same clock, and each of them keeps its own interval.</p>
 *
 * <p>Change is decided by a KEY, not by the value. With the default key - the value itself - an
 * object <b>mutated in place</b> is invisible: it is the same instance, so it equals itself and
 * nothing invalidates. Give a key that moves when the object does (a version counter, a hash, the
 * one field the screen shows), or call {@code ctx.refresh()} by hand. The buffer's output diff is
 * still the backstop: a repaint that changes nothing costs no write.</p>
 */
public final class WatchState<T> extends AbstractState<T> {

    private final Supplier<T> source;
    private final Function<T, ?> key;
    private final long intervalTicks;

    private T value;
    private Object currentKey;
    private long ticksSinceRead = 0L;

    /** A watch read on every tick it is polled on. */
    public WatchState(Supplier<T> source, Function<T, ?> key) {
        this(source, key, 1L);
    }

    /**
     * @param intervalTicks how many ticks pass between two reads of {@code source} - {@code 20} reads
     *                      once a second
     * @throws IllegalArgumentException when the interval is below one tick
     */
    public WatchState(Supplier<T> source, Function<T, ?> key, long intervalTicks) {
        if (source == null || key == null) {
            throw new IllegalArgumentException("A watch needs a source and a key function.");
        }
        if (intervalTicks < 1L) {
            throw new IllegalArgumentException("A watch asked for a read every " + intervalTicks
                    + " ticks, which is never. A value the screen is not to look at on a clock needs no "
                    + "watch at all: read it while rendering, and depend on a state that moves when it does.");
        }
        this.source = source;
        this.key = key;
        this.intervalTicks = intervalTicks;
        this.value = source.get();
        this.currentKey = keyOf(this.value);
    }

    @Override
    public T get() {
        return value;
    }

    /**
     * One tick of whatever clock drives this watch. The source is read on every interval-th of them,
     * and the dependents are invalidated only when the key moved.
     */
    public void poll() {
        if (++ticksSinceRead < intervalTicks) {
            return;
        }
        ticksSinceRead = 0L;
        T next = source.get();
        Object nextKey = keyOf(next);
        if (Objects.equals(currentKey, nextKey)) {
            return;
        }
        this.value = next;
        this.currentKey = nextKey;
        invalidate();
    }

    private Object keyOf(T value) {
        return value == null ? null : key.apply(value);
    }

    @Override
    public String toString() {
        return "WatchState{" + value + "}";
    }

}
