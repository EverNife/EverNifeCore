package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

/**
 * The regions of a plugin could not be read. Until a later load succeeds they do not exist, so nothing they would
 * deny is denied; the load is retried on its own. The owner decides whether that is reason to refuse its enable.
 */
public class RegionLoadFailedEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionLoadFailedEvent.class);
    }

    private final ECPluginData owner;
    private final @Nullable String world;
    private final Throwable error;

    public RegionLoadFailedEvent(ECPluginData owner, @Nullable String world, Throwable error) {
        this.owner = owner;
        this.world = world;
        this.error = error;
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** The world whose regions failed; null when the owner's whole load failed. */
    public @Nullable String getWorld() {
        return world;
    }

    public Throwable getError() {
        return error;
    }
}
