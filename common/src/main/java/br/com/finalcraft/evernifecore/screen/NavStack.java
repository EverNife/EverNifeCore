package br.com.finalcraft.evernifecore.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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

    /**
     * Puts {@code next} where {@code old} is, at whatever depth, leaving every other screen where it was.
     *
     * @throws IllegalArgumentException when {@code old} is not in this stack, or {@code next} is null
     */
    public void replace(T old, T next) {
        if (next == null) {
            throw new IllegalArgumentException("A NavStack does not hold null screens; to drop one, use remove(old).");
        }
        if (!swap(old, next)) {
            throw new IllegalArgumentException("The screen to replace is not in this stack, so there is no place to put "
                    + "the new one. Replace a screen the stack holds, or push the new one instead.");
        }
    }

    /** Whether {@code screen} is in the stack, at whatever depth. */
    public boolean contains(T screen) {
        return screens.contains(screen);
    }

    /** Takes {@code screen} out of the stack, at whatever depth. Whether it was there. */
    public boolean remove(T screen) {
        return swap(screen, null);
    }

    //a Deque has no indexed set: rebuild it, top first, with the one entry changed
    private boolean swap(T old, T next) {
        List<T> topFirst = new ArrayList<>(screens);
        int index = topFirst.indexOf(old);
        if (index < 0) {
            return false;
        }
        if (next == null) {
            topFirst.remove(index);
        } else {
            topFirst.set(index, next);
        }
        screens.clear();
        screens.addAll(topFirst);
        return true;
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
