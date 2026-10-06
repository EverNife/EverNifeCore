package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;

/** A region left the index. Whether it also left storage depends on the cause: only {@link Cause#API} and {@link Cause#EXPIRED} delete it. */
public class RegionRemovedEvent extends RegionEvent {

    public enum Cause {
        /** The owner removed it. */
        API,
        /** Its owner disabled; it comes back on the next load. */
        OWNER_DISABLED,
        /** Its deadline passed. */
        EXPIRED,
        /** A transient region whose world unloaded. */
        WORLD_UNLOADED
    }

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionRemovedEvent.class);
    }

    private final Cause cause;

    public RegionRemovedEvent(Region region, Cause cause) {
        super(region);
        this.cause = cause;
    }

    public Cause getCause() {
        return cause;
    }
}
