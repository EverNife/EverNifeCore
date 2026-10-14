package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionClock;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.MembershipCheck;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        if (check.getType() == MembershipCheck.Type.EXACT) {
            return compute.getAsBoolean();
        }
        Cached cached = memberships.get(group);
        if (cached != null && isFresh(cached, check)) {
            return cached.value;
        }
        boolean value = compute.getAsBoolean();
        memberships.put(group, new Cached(value, boundaryEpoch, RegionClock.now()));
        return value;
    }

    // ------------------------------------------------------------------
    //  Where the player is - written by the movement tracker, on the thread of the player's world
    // ------------------------------------------------------------------

    /** How many accepted blocks the trail keeps. */
    public static final int TRAIL_SIZE = 16;

    private volatile @Nullable FPlayer online;
    private volatile List<Region> current = Collections.emptyList();
    private volatile @Nullable WorldLocPos position;
    private volatile long snapshotVersion;
    private final Deque<WorldBlockPos> trail = new ArrayDeque<>(TRAIL_SIZE);
    private final Deque<Instant> denials = new ArrayDeque<>();
    private @Nullable FlagKey denialFlag;
    private final Map<FlagKey, Object> appliedValues = new ConcurrentHashMap<>();
    private final Map<FlagKey, Region> appliedSources = new ConcurrentHashMap<>();
    private final Map<FlagKey, Instant> nextRevalidation = new ConcurrentHashMap<>();
    private volatile boolean revalidateAll;

    /** The player as the tracker last saw them; null before the first join or move. */
    public @Nullable FPlayer getOnlinePlayer() {
        return online;
    }

    void setOnlinePlayer(FPlayer player) {
        this.online = player;
    }

    /** The regions the player is in, as of {@link #getSnapshotVersion()}. Read-only. */
    public List<Region> getCurrent() {
        return current;
    }

    /** Where the player last stood with the move accepted; null before the first join or move. */
    public @Nullable WorldLocPos getPosition() {
        return position;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    /** The value of a session flag applied to this player right now; null when none is. */
    public @Nullable Object getAppliedSessionValue(RegionFlag<?> flag) {
        return appliedValues.get(flag.getKey());
    }

    /** When {@code flag} is looked at again on its own; null when no check is pending for it. */
    public @Nullable Instant getNextRevalidation(RegionFlag<?> flag) {
        return nextRevalidation.get(flag.getKey());
    }

    /** Whether {@code to} is in the world and block the player already stands in. */
    boolean isAt(WorldLocPos to) {
        WorldLocPos at = position;
        return at != null && at.getWorldName().equals(to.getWorldName()) && block(at).equals(block(to));
    }

    /** A move inside the same regions was accepted. */
    void moveTo(WorldLocPos to) {
        position = to;
        pushAccepted(block(to));
        clearDenials();
    }

    /** A move into another set of regions was accepted. */
    void commit(List<Region> regions, WorldLocPos to, long version) {
        current = Collections.unmodifiableList(regions);
        snapshotVersion = version;
        moveTo(to);
        markBoundaryCrossed();
    }

    /** The regions under a player who did not move changed. */
    void setCurrent(List<Region> regions, long version) {
        current = Collections.unmodifiableList(regions);
        snapshotVersion = version;
        markBoundaryCrossed();
    }

    /** Whether a change of {@code region} can change what this player is under: they are in it, or in a descendant of it. */
    public boolean isAffectedBy(Region region) {
        for (Region in : current) {
            for (Region chain = in; chain != null; chain = chain.getParent()) {
                if (chain == region) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- trail ----

    /** Internal: records an accepted block; the oldest falls off after {@link #TRAIL_SIZE}. */
    public void pushAccepted(WorldBlockPos block) {
        synchronized (trail) {
            if (block.equals(trail.peekLast())) {
                return;
            }
            if (trail.size() == TRAIL_SIZE) {
                trail.removeFirst();
            }
            trail.addLast(block);
        }
    }

    /** How many accepted blocks the trail holds. */
    public int getTrailSize() {
        synchronized (trail) {
            return trail.size();
        }
    }

    /** The center of the last accepted block; null when nothing was accepted yet. */
    public @Nullable WorldLocPos lastValid() {
        return trailBack(0);
    }

    /**
     * The center of the {@code stepsBack}-th accepted block before the last one - 0 is the last - or of the oldest one
     * the trail still holds when it is shorter; null when nothing was accepted yet.
     */
    public @Nullable WorldLocPos trailBack(int stepsBack) {
        synchronized (trail) {
            if (trail.isEmpty()) {
                return null;
            }
            Iterator<WorldBlockPos> newestFirst = trail.descendingIterator();
            WorldBlockPos found = newestFirst.next();
            for (int step = 0; step < stepsBack && newestFirst.hasNext(); step++) {
                found = newestFirst.next();
            }
            return WorldLocPos.of(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, found.getWorldName());
        }
    }

    // ---- denials ----

    /**
     * Internal: records a denial by {@code flag} and returns how many denials of that flag fall inside the last
     * {@code window}, this one included. A denial by another flag starts the count over.
     */
    public int noteDenial(FlagKey flag, Instant now, Duration window) {
        synchronized (denials) {
            if (!flag.equals(denialFlag)) {
                denials.clear();
                denialFlag = flag;
            }
            Instant since = now.minus(window);
            while (!denials.isEmpty() && denials.peekFirst().isBefore(since)) {
                denials.removeFirst();
            }
            denials.addLast(now);
            return denials.size();
        }
    }

    /** The denials counted by the last {@link #noteDenial}; 0 after an accepted move. */
    public int getDenials() {
        synchronized (denials) {
            return denials.size();
        }
    }

    void clearDenials() {
        synchronized (denials) {
            denials.clear();
            denialFlag = null;
        }
    }

    // ---- session flags ----

    @Nullable Region getAppliedSource(FlagKey flag) {
        return appliedSources.get(flag);
    }

    Map<FlagKey, Object> getAppliedValues() {
        return appliedValues;
    }

    void setApplied(FlagKey flag, @Nullable Object value, @Nullable Region source) {
        if (value == null) {
            appliedValues.remove(flag);
            appliedSources.remove(flag);
        } else {
            appliedValues.put(flag, value);
            appliedSources.put(flag, source);
        }
    }

    void setNextRevalidation(FlagKey flag, @Nullable Instant at) {
        if (at == null) {
            nextRevalidation.remove(flag);
        } else {
            nextRevalidation.put(flag, at);
        }
    }

    boolean isDue(FlagKey flag, Instant now) {
        Instant at = nextRevalidation.get(flag);
        return at != null && !now.isBefore(at);
    }

    /** Whether a check is waiting: an invalidation, or a flag whose next check is not after {@code now}. */
    boolean hasDueCheck(Instant now) {
        if (revalidateAll) {
            return true;
        }
        for (Instant at : nextRevalidation.values()) {
            if (!now.isBefore(at)) {
                return true;
            }
        }
        return false;
    }

    boolean hasPendingCheck() {
        return revalidateAll || !nextRevalidation.isEmpty();
    }

    /** Internal: every session flag of this player is looked at again on the next check. */
    public void markRevalidateAll() {
        revalidateAll = true;
    }

    boolean takeRevalidateAll() {
        boolean was = revalidateAll;
        revalidateAll = false;
        return was;
    }

    private static WorldBlockPos block(WorldLocPos at) {
        return WorldBlockPos.of((int) Math.floor(at.getX()), (int) Math.floor(at.getY()), (int) Math.floor(at.getZ()), at.getWorldName());
    }

    private boolean isFresh(Cached cached, MembershipCheck check) {
        if (check.getType() == MembershipCheck.Type.EXACT || cached.epoch != boundaryEpoch) {
            return false;
        }
        Duration interval = check.getInterval();
        return interval == null || Duration.between(cached.at, RegionClock.now()).compareTo(interval) < 0;
    }
}
