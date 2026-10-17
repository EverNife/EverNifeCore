package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;

/** The terrain of a region was saved or is being put back by its {@link RegionTerrain}. */
public class RegionTerrainEvent extends RegionEvent {

    public enum Type {
        /** A snapshot was written. */
        SNAPSHOT,
        /** A restore - of the journal or of a snapshot - starts. */
        RESTORE_START,
        /** One tick of that restore ran: {@link #getBlocks()} put back in {@link #getNanos()}. */
        RESTORE_TICK,
        /** That restore ended, done or cut short. */
        RESTORE_END
    }

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionTerrainEvent.class);
    }

    private final Type type;
    private final boolean journal;
    private final int blocks;
    private final long nanos;

    public RegionTerrainEvent(Region region, Type type, boolean journal) {
        this(region, type, journal, 0, 0);
    }

    public RegionTerrainEvent(Region region, Type type, boolean journal, int blocks, long nanos) {
        super(region);
        this.type = type;
        this.journal = journal;
        this.blocks = blocks;
        this.nanos = nanos;
    }

    public Type getType() {
        return type;
    }

    /** True for the change journal, false for a snapshot. */
    public boolean isJournal() {
        return journal;
    }

    /**
     * {@link Type#RESTORE_TICK}: the positions that tick put back - for a snapshot its whole budget, the last tick
     * included; 0 for the other types.
     */
    public int getBlocks() {
        return blocks;
    }

    /**
     * The time the work took on the world's thread: writing the file for {@link Type#SNAPSHOT}, the tick's placing for
     * {@link Type#RESTORE_TICK}; 0 for the other types.
     */
    public long getNanos() {
        return nanos;
    }
}
