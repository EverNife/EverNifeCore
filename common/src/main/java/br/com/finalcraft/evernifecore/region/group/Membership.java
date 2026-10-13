package br.com.finalcraft.evernifecore.region.group;

import br.com.finalcraft.evernifecore.region.RegionClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** An explicit member of an {@link AccessGroup}: who, since when, and until when (null = for good). */
public final class Membership {

    private final UUID uuid;
    private final Instant joinedAt;
    private final @Nullable Instant expiresAt;

    Membership(UUID uuid, Instant joinedAt, @Nullable Instant expiresAt) {
        this.uuid = uuid;
        this.joinedAt = joinedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** Null when permanent; never negative. */
    public @Nullable Duration getRemaining() {
        if (expiresAt == null) {
            return null;
        }
        Duration remaining = Duration.between(RegionClock.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** An expired membership reads as no membership. */
    public boolean isExpired() {
        return expiresAt != null && !RegionClock.now().isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return expiresAt == null ? uuid.toString() : uuid + " until " + expiresAt;
    }
}
