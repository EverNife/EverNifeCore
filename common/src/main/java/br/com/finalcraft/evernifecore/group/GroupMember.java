package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** An explicit member of a {@link Group}: who, since when, until when (null = for good) and with which roles. Immutable. */
public final class GroupMember {

    private final UUID uuid;
    private final Instant joinedAt;
    private final @Nullable Instant expiresAt;
    private final Set<GroupRole> roles;
    private final Set<String> unknownRoles;

    GroupMember(UUID uuid, Instant joinedAt, @Nullable Instant expiresAt, Set<GroupRole> roles, Set<String> unknownRoles) {
        this.uuid = uuid;
        this.joinedAt = joinedAt;
        this.expiresAt = expiresAt;
        this.roles = roles.isEmpty() ? Collections.<GroupRole>emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.unknownRoles = unknownRoles.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(unknownRoles));
    }

    GroupMember withRoles(Set<GroupRole> newRoles) {
        return new GroupMember(uuid, joinedAt, expiresAt, newRoles, unknownRoles);
    }

    GroupMember withExpiresAt(@Nullable Instant newExpiresAt) {
        return new GroupMember(uuid, joinedAt, newExpiresAt, roles, unknownRoles);
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
        Duration remaining = Duration.between(ECClock.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** An expired member reads as no member. */
    public boolean isExpired() {
        return expiresAt != null && !ECClock.now().isBefore(expiresAt);
    }

    /** The roles of the kind's ladder the member holds. Read-only. */
    public Set<GroupRole> getRoles() {
        return roles;
    }

    /**
     * Internal: stored role names the kind does not list any more, kept as stored so they come back if the kind lists
     * them again.
     */
    public Set<String> getUnknownRoles() {
        return unknownRoles;
    }

    /** The highest rank among the roles; 0 with no role. */
    public int getRank() {
        int rank = 0;
        boolean any = false;
        for (GroupRole role : roles) {
            rank = any ? Math.max(rank, role.getRank()) : role.getRank();
            any = true;
        }
        return rank;
    }

    public boolean hasRole(String name) {
        for (GroupRole role : roles) {
            if (role.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return uuid + (roles.isEmpty() ? "" : " " + roles) + (expiresAt == null ? "" : " until " + expiresAt);
    }
}
