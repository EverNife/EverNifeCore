package br.com.finalcraft.evernifecore.minecraft.gui.nav;

/**
 * What a {@link NavStack} pop produced: the screen that came back into view, and the value the
 * screen that left handed to it. Both may be absent - popping the last entry reveals no screen, and
 * a plain {@code pop} carries no value.
 */
public final class NavResult<T> {

    private final T screen;
    private final Object value;

    NavResult(T screen, Object value) {
        this.screen = screen;
        this.value = value;
    }

    /** The screen now on top, or {@code null} when the stack ran out. */
    public T getScreen() {
        return screen;
    }

    public boolean hasScreen() {
        return screen != null;
    }

    /** The value handed back, or {@code null}. */
    public Object getValue() {
        return value;
    }

    /** The value handed back when it is of the expected type, {@code null} otherwise. */
    public <R> R getValue(Class<R> type) {
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public String toString() {
        return "NavResult{screen=" + screen + ", value=" + value + "}";
    }

}
