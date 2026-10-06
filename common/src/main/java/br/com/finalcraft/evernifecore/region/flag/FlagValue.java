package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.region.RegionTime;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A flag value as a region or group holds it: the value and, when it is temporary, the instant it stops counting. */
public final class FlagValue<T> {

    private final T value;
    private final @Nullable Instant expiresAt;

    private FlagValue(T value, @Nullable Instant expiresAt) {
        this.value = Objects.requireNonNull(value, "A flag value cannot be null: remove the flag instead of setting it to null.");
        this.expiresAt = expiresAt;
    }

    public static <T> FlagValue<T> permanent(T value) {
        return new FlagValue<>(value, null);
    }

    public static <T> FlagValue<T> until(T value, Instant expiresAt) {
        return new FlagValue<>(value, Objects.requireNonNull(expiresAt, "expiresAt"));
    }

    public T getValue() {
        return value;
    }

    /** Null when the value is permanent. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** True once {@link RegionTime#now()} reaches the deadline; an expired value reads as not set. */
    public boolean isExpired() {
        return expiresAt != null && !RegionTime.now().isBefore(expiresAt);
    }

    /** Null when the value is permanent; never negative. */
    public @Nullable Duration getRemaining() {
        if (expiresAt == null) {
            return null;
        }
        Duration remaining = Duration.between(RegionTime.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FlagValue)) {
            return false;
        }
        FlagValue<?> that = (FlagValue<?>) other;
        return value.equals(that.value) && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return 31 * value.hashCode() + Objects.hashCode(expiresAt);
    }

    @Override
    public String toString() {
        return expiresAt == null ? String.valueOf(value) : value + " until " + expiresAt;
    }
}
