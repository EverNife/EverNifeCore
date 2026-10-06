package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.shape.Shape;

/** A region got a new shape, already published to the index. */
public class RegionRedefinedEvent extends RegionEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionRedefinedEvent.class);
    }

    private final Shape oldShape;
    private final Shape newShape;

    public RegionRedefinedEvent(Region region, Shape oldShape, Shape newShape) {
        super(region);
        this.oldShape = oldShape;
        this.newShape = newShape;
    }

    public Shape getOldShape() {
        return oldShape;
    }

    public Shape getNewShape() {
        return newShape;
    }
}
