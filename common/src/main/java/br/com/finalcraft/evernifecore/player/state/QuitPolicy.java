package br.com.finalcraft.evernifecore.player.state;

import jakarta.annotation.Nullable;

import java.time.Duration;

/** What one holder of a {@link HeldPlayerState} slot does when the player quits while holding it. */
public final class QuitPolicy {

    private static final QuitPolicy GIVE_BACK = new QuitPolicy(null, false);
    private static final QuitPolicy DELEGATE = new QuitPolicy(null, true);

    private final @Nullable Duration window;
    private final boolean delegate;

    private QuitPolicy(@Nullable Duration window, boolean delegate) {
        this.window = window;
        this.delegate = delegate;
    }

    /** The holder lets go on the quit, while the player can still be written to. The default. */
    public static QuitPolicy giveBack() {
        return GIVE_BACK;
    }

    /**
     * The slot stays on the player, saved with them, for {@code window}: reconnecting in time finds it still held. Past
     * that {@link HeldStateQuitEvent.Expired} is posted, and the next join gives the player's own back.
     */
    public static QuitPolicy keepFor(Duration window) {
        return new QuitPolicy(requireWindow(window), false);
    }

    /** Posts {@link HeldStateQuitEvent.Delegated} so the plugin decides on the spot; no answer gives back. */
    public static QuitPolicy delegate() {
        return DELEGATE;
    }

    static Duration requireWindow(Duration window) {
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("A reconnection window of " + window + " ends before it starts: give it a"
                    + " duration above zero, or use QuitPolicy.giveBack() to give the slot back on the quit.");
        }
        return window;
    }

    /** The window of {@link #keepFor}; null for the other two. */
    public @Nullable Duration getWindow() {
        return window;
    }

    public boolean isDelegate() {
        return delegate;
    }
}
