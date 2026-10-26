package br.com.finalcraft.evernifecore.flag;

import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Where a {@link FlagLayer} sorts: {@code lock} first, then the written priority (highest first), then the contextual
 * order - a group of a region that contains the player, then the region itself, each by the region's priority (highest
 * first), then the holders that follow the player ({@link ActorHolders} order). The newest {@link FlagValue#getSince()}
 * breaks what is left, outside the rank: two layers of equal rank are "equally specific", which
 * {@link FlagResolvers#preferAmongEquals} reads.
 */
public final class LayerRank implements Comparable<LayerRank> {

    private static final int TIER_PLACE = 0;
    private static final int TIER_ACTOR = 1;

    private final boolean lock;
    private final int priority;
    private final int tier;
    private final int band;
    private final int kind;
    private final int order;

    private LayerRank(boolean lock, int priority, int tier, int band, int kind, int order) {
        this.lock = lock;
        this.priority = priority;
        this.tier = tier;
        this.band = band;
        this.kind = kind;
        this.order = order;
    }

    /**
     * Internal: a layer of a region chain.
     *
     * @param regionPriority the priority of the candidate region the layer belongs to
     * @param groupPriority  the group's priority for a group value; null for the region's own (or inherited) value
     */
    public static LayerRank place(FlagValue<?> value, int regionPriority, @Nullable Integer groupPriority) {
        return groupPriority == null
                ? new LayerRank(value.isLock(), value.getPriority(), TIER_PLACE, regionPriority, 1, 0)
                : new LayerRank(value.isLock(), value.getPriority(), TIER_PLACE, regionPriority, 0, -groupPriority);
    }

    /** Internal: a layer of the {@code index}-th holder that follows the player. */
    public static LayerRank actor(FlagValue<?> value, int index) {
        return new LayerRank(value.isLock(), value.getPriority(), TIER_ACTOR, 0, 0, index);
    }

    public boolean isLock() {
        return lock;
    }

    /** The written priority. */
    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(LayerRank other) {
        if (lock != other.lock) {
            return lock ? -1 : 1;
        }
        int compared = Integer.compare(other.priority, priority);
        if (compared == 0) {
            compared = Integer.compare(tier, other.tier);
        }
        if (compared == 0) {
            compared = Integer.compare(other.band, band);
        }
        if (compared == 0) {
            compared = Integer.compare(kind, other.kind);
        }
        return compared != 0 ? compared : Integer.compare(order, other.order);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LayerRank && compareTo((LayerRank) o) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lock, priority, tier, band, kind, order);
    }

    @Override
    public String toString() {
        return (lock ? "lock " : "") + "prio " + priority + (tier == TIER_ACTOR ? " actor #" + order : " band " + band + (kind == 0 ? " group" : ""));
    }
}
