package br.com.finalcraft.evernifecore.minecraft.gui.state;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A state read from somewhere else: a domain object that changes on its own, watched instead of
 * mirrored.
 *
 * <p>It does not schedule anything and does not know what a tick is - {@link #poll()} is called from
 * outside, once per tick of an open view, which is what keeps this type free of the platform.</p>
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

    private T value;
    private Object currentKey;

    public WatchState(Supplier<T> source, Function<T, ?> key) {
        if (source == null || key == null) {
            throw new IllegalArgumentException("A watch needs a source and a key function.");
        }
        this.source = source;
        this.key = key;
        this.value = source.get();
        this.currentKey = keyOf(this.value);
    }

    @Override
    public T get() {
        return value;
    }

    /** Reads the source once. Invalidates the dependents only when the key moved. */
    public void poll() {
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
