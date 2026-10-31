package br.com.finalcraft.evernifecore.group;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** When an ephemeral group ends on its own. Immutable. */
public final class Dissolution {

    private static final Dissolution WHEN_EMPTY = new Dissolution(null, null);

    private final @Nullable Duration after;
    private final @Nullable Instant at;

    private Dissolution(@Nullable Duration after, @Nullable Instant at) {
        this.after = after;
        this.at = at;
    }

    /** The last member gone, whatever the cause, dissolves it with {@link DissolveCause#EMPTY}. */
    public static Dissolution whenEmpty() {
        return WHEN_EMPTY;
    }

    /**
     * {@code duration} after creation the sweeper dissolves it with {@link DissolveCause#EXPIRED}.
     *
     * @throws IllegalArgumentException when the duration is not positive
     */
    public static Dissolution after(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Dissolution.after needs a positive duration, not " + duration
                    + ": use whenEmpty() for a group that ends with its last member.");
        }
        return new Dissolution(duration, null);
    }

    /** At {@code when} the sweeper dissolves it with {@link DissolveCause#EXPIRED}. */
    public static Dissolution at(Instant when) {
        return new Dissolution(null, Objects.requireNonNull(when, "when"));
    }

    public boolean isWhenEmpty() {
        return after == null && at == null;
    }

    /** The deadline for a group created at {@code createdAt}; null for {@link #whenEmpty()}. */
    public @Nullable Instant deadlineFor(Instant createdAt) {
        return at != null ? at : after != null ? createdAt.plus(after) : null;
    }
}
