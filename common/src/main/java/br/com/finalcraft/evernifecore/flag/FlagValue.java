package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A flag value as a {@link FlagHolder} holds it, with its envelope: how it meets the layers after it ({@link FlagMode}),
 * a written priority, a lock, when it was set and, when temporary, when it stops counting. Immutable; the
 * {@code with*} methods return a copy.
 */
public final class FlagValue<T> {

    private final T value;
    private final @Nullable Instant expiresAt;
    private final FlagMode mode;
    private final int priority;
    private final boolean lock;
    private final Instant since;

    private FlagValue(T value, @Nullable Instant expiresAt, FlagMode mode, int priority, boolean lock, Instant since) {
        this.value = Objects.requireNonNull(value, "A flag value cannot be null: remove the flag instead of setting it to null.");
        this.expiresAt = expiresAt;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.priority = priority;
        this.lock = lock;
        this.since = Objects.requireNonNull(since, "since");
    }

    /** {@link FlagMode#ADD}, priority 0, no lock, set now. */
    public static <T> FlagValue<T> permanent(T value) {
        return new FlagValue<>(value, null, FlagMode.ADD, 0, false, ECClock.now());
    }

    public static <T> FlagValue<T> until(T value, Instant expiresAt) {
        return new FlagValue<>(value, Objects.requireNonNull(expiresAt, "expiresAt"), FlagMode.ADD, 0, false, ECClock.now());
    }

    public FlagValue<T> withMode(FlagMode mode) {
        return new FlagValue<>(value, expiresAt, mode, priority, lock, since);
    }

    /** Compared before the contextual order: a higher written priority comes first wherever the layers meet. */
    public FlagValue<T> withPriority(int priority) {
        return new FlagValue<>(value, expiresAt, mode, priority, lock, since);
    }

    /** A marker, not a big number: a locked layer comes before every unlocked one. Only a PLACE holder takes it. */
    public FlagValue<T> withLock(boolean lock) {
        return new FlagValue<>(value, expiresAt, mode, priority, lock, since);
    }

    public FlagValue<T> withSince(Instant since) {
        return new FlagValue<>(value, expiresAt, mode, priority, lock, since);
    }

    /** The same envelope over another value or deadline. */
    <V> FlagValue<V> rewrap(V newValue, @Nullable Instant newExpiresAt) {
        return new FlagValue<>(newValue, newExpiresAt, mode, priority, lock, since);
    }

    public T getValue() {
        return value;
    }

    public FlagMode getMode() {
        return mode;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isLock() {
        return lock;
    }

    /** When it was set; the newest wins an otherwise equal order. A value stored before the envelope existed reads {@link Instant#EPOCH}. */
    public Instant getSince() {
        return since;
    }

    /** Null when the value is permanent. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** True once {@link ECClock#now()} reaches the deadline; an expired value reads as not set. */
    public boolean isExpired() {
        return expiresAt != null && !ECClock.now().isBefore(expiresAt);
    }

    /** Null when the value is permanent; never negative. */
    public @Nullable Duration getRemaining() {
        if (expiresAt == null) {
            return null;
        }
        Duration remaining = Duration.between(ECClock.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** The envelope as {@code [mode=set] [prio=10] [lock]}, each part only when it is not the default; empty for the default envelope. */
    public String describeEnvelope() {
        StringBuilder text = new StringBuilder();
        if (mode == FlagMode.SET) {
            text.append(" mode=set");
        }
        if (priority != 0) {
            text.append(" prio=").append(priority);
        }
        if (lock) {
            text.append(" lock");
        }
        return text.toString().trim();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FlagValue)) {
            return false;
        }
        FlagValue<?> that = (FlagValue<?>) other;
        return value.equals(that.value) && Objects.equals(expiresAt, that.expiresAt) && mode == that.mode && priority == that.priority
                && lock == that.lock && since.equals(that.since);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, expiresAt, mode, priority, lock, since);
    }

    @Override
    public String toString() {
        String envelope = describeEnvelope();
        return value + (envelope.isEmpty() ? "" : " [" + envelope + "]") + (expiresAt == null ? "" : " until " + expiresAt);
    }
}
