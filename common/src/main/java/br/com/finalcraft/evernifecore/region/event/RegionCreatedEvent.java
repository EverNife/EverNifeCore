package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.overlap.Overlap;

import java.util.Collections;
import java.util.List;

/** A region was created and is already in the index. Not cancellable: a plugin refuses with a {@code RegionPolicy}. */
public class RegionCreatedEvent extends RegionEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionCreatedEvent.class);
    }

    private final List<Overlap> overlaps;

    public RegionCreatedEvent(Region region, List<Overlap> overlaps) {
        super(region);
        this.overlaps = Collections.unmodifiableList(overlaps);
    }

    /** What the new region shares blocks with, as the creation's dry run found it. */
    public List<Overlap> getOverlaps() {
        return overlaps;
    }
}
