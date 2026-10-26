package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/** An invitation of one player into a {@link Group}, valid until {@link #getExpiresAt()}. Immutable. */
public final class Invite {

    private final GroupId group;
    private final UUID player;
    private final @Nullable UUID invitedBy;
    private final Instant invitedAt;
    private final Instant expiresAt;

    Invite(GroupId group, UUID player, @Nullable UUID invitedBy, Instant invitedAt, Instant expiresAt) {
        this.group = group;
        this.player = player;
        this.invitedBy = invitedBy;
        this.invitedAt = invitedAt;
        this.expiresAt = expiresAt;
    }

    public GroupId getGroup() {
        return group;
    }

    public UUID getPlayer() {
        return player;
    }

    /** Who invited; null when the owner did. */
    public @Nullable UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return !ECClock.now().isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return player + " into " + group + " until " + expiresAt;
    }
}
