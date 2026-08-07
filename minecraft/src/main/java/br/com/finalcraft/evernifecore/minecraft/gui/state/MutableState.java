package br.com.finalcraft.evernifecore.minecraft.gui.state;

import java.util.function.Function;

/**
 * A state whose value is written by the plugin. Create one with {@link State#of(Object)} to share it
 * between components, or through the component itself to scope it to one gui.
 */
public class MutableState<T> extends AbstractState<T> {

    private volatile T value;

    public MutableState(T initial) {
        this.value = initial;
    }

    @Override
    public T get() {
        return value;
    }

    /**
     * Writes the value and invalidates every dependent - <b>always</b>, even when the new value
     * equals the old one.
     *
     * <p>An object mutated in place has no way to prove it changed, so refusing to invalidate on
     * {@code equals} would leave the screen stale. The wasted work stops at the buffer: the commit
     * compares rendered output and writes only the slots that actually differ.</p>
     */
    public void set(T value) {
        this.value = value;
        invalidate();
    }

    /** Writes what {@code mutator} makes of the current value. It reads through {@link #get()}, so a
     *  state whose value lives elsewhere updates the value that is actually there. */
    public void update(Function<T, T> mutator) {
        set(mutator.apply(get()));
    }

    @Override
    public String toString() {
        return "MutableState{" + value + "}";
    }

}
