package br.com.finalcraft.evernifecore.minecraft.gui.nav;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The back stack of a viewer: as many levels deep as were opened, and able to hand a value back to
 * the screen underneath.
 *
 * <p>A pure primitive - it holds screens, never opens them. Wiring it to a viewer's context is done
 * by the view layer.</p>
 */
public final class NavStack<T> {

    private final Deque<T> screens = new ArrayDeque<>();

    public void push(T screen) {
        if (screen == null) {
            throw new IllegalArgumentException("A NavStack does not hold null screens - popping one "
                    + "would be indistinguishable from an empty stack.");
        }
        screens.push(screen);
    }

    /** Drops the current screen and reveals the one below it. */
    public NavResult<T> pop() {
        return popWith(null);
    }

    /** Drops the current screen and hands {@code value} to the one below it. */
    public NavResult<T> popWith(Object value) {
        if (screens.isEmpty()) {
            return new NavResult<>(null, value);
        }
        screens.pop();
        return new NavResult<>(screens.peek(), value);
    }

    /** The current screen, or {@code null}. */
    public T peek() {
        return screens.peek();
    }

    public int size() {
        return screens.size();
    }

    public boolean isEmpty() {
        return screens.isEmpty();
    }

    public void clear() {
        screens.clear();
    }

    @Override
    public String toString() {
        return "NavStack{depth=" + screens.size() + "}";
    }

}
