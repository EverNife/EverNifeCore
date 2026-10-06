package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;

import java.util.Objects;

/**
 * Something happened to one region. Every region event is posted only when somebody listens, on the thread of
 * whoever made the change; a subscriber that cares about one plugin's regions filters by {@link #getOwner()}.
 */
public abstract class RegionEvent extends ECEvent implements IECEvent {

    private final Region region;

    protected RegionEvent(Region region) {
        this.region = Objects.requireNonNull(region, "'region' cannot be null when building a region event!");
    }

    public Region getRegion() {
        return region;
    }

    /** The plugin that owns the region. */
    public ECPluginData getOwner() {
        return region.getOwner();
    }
}
