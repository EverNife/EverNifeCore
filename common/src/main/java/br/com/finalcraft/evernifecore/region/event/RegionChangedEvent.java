package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;

/** A property of a region changed - one with no event of its own. */
public class RegionChangedEvent extends RegionEvent {

    public enum Type {
        PRIORITY, PARENT, TAGS, METADATA, EXPIRES_AT,
        /** The region's own settings override of one flag was set or removed. */
        FLAG_SETTINGS
    }

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionChangedEvent.class);
    }

    private final Type type;

    public RegionChangedEvent(Region region, Type type) {
        super(region);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
