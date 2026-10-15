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
        /** That restore ended, done or cut short. */
        RESTORE_END
    }

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionTerrainEvent.class);
    }

    private final Type type;
    private final boolean journal;

    public RegionTerrainEvent(Region region, Type type, boolean journal) {
        super(region);
        this.type = type;
        this.journal = journal;
    }

    public Type getType() {
        return type;
    }

    /** True for the change journal, false for a snapshot. */
    public boolean isJournal() {
        return journal;
    }
}
