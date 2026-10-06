package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.MembershipCheck;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * What the region system remembers about one online player. Transient: it lives while the player is online and
 * is never persisted. Obtained through {@code RegionLookup.getSession(player)}.
 */
public final class RegionPlayerSession {

    private static final class Cached {
        final boolean value;
        final long epoch;
        final Instant at;

        Cached(boolean value, long epoch, Instant at) {
            this.value = value;
            this.epoch = epoch;
            this.at = at;
        }
    }

    private final UUID player;
    private final ConcurrentHashMap<AccessGroup, Cached> memberships = new ConcurrentHashMap<>();
    //bumped whenever the set of regions the player is in changes; a cached answer from an older epoch is stale
    private volatile long boundaryEpoch;

    /** Internal: sessions are made by {@code RegionLookup.getSession(player)}. */
    public RegionPlayerSession(UUID player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public UUID getPlayer() {
        return player;
    }

    /** The cached answer of {@code group.contains(player)}; null when nothing valid is cached - always so for an exact check. */
    public @Nullable Boolean getCachedMembership(AccessGroup group) {
        Cached cached = memberships.get(group);
        return cached == null || !isFresh(cached, group.getMembershipCheck()) ? null : cached.value;
    }

    /** Forgets every cached answer. */
    public void invalidate() {
        memberships.clear();
    }

    /** Internal: forgets the cached answer for {@code group}. */
    public void invalidate(AccessGroup group) {
        memberships.remove(group);
    }

    /** Internal: the set of regions the player is in changed, so boundary-bound answers are stale. */
    public void markBoundaryCrossed() {
        boundaryEpoch++;
    }

    /**
     * Internal: the answer for {@code group} under {@code check} - the cached one while it is fresh, otherwise
     * {@code compute}'s, cached. An exact check is never cached.
     */
    public boolean membership(AccessGroup group, MembershipCheck check, BooleanSupplier compute) {
        if (check.getKind() == MembershipCheck.Kind.EXACT) {
            return compute.getAsBoolean();
        }
        Cached cached = memberships.get(group);
        if (cached != null && isFresh(cached, check)) {
            return cached.value;
        }
        boolean value = compute.getAsBoolean();
        memberships.put(group, new Cached(value, boundaryEpoch, RegionTime.now()));
        return value;
    }

    private boolean isFresh(Cached cached, MembershipCheck check) {
        if (check.getKind() == MembershipCheck.Kind.EXACT || cached.epoch != boundaryEpoch) {
            return false;
        }
        Duration interval = check.getInterval();
        return interval == null || Duration.between(cached.at, RegionTime.now()).compareTo(interval) < 0;
    }
}
