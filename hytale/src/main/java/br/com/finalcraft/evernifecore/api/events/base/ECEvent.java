package br.com.finalcraft.evernifecore.api.events.base;

import com.hypixel.hytale.event.IEvent;

/**
 * The Hytale face of {@link IECEvent}: an EC event that extends this IS a Hytale event, so it can be
 * dispatched into the server's own bus once mirrored.
 */
public abstract class ECEvent implements IEvent<Void>, IECEvent {

    protected ECEvent() {
    }

    /** Hytale has no per-event async flag: the value is accepted so the event compiles here, and ignored. */
    protected ECEvent(boolean async) {
    }

    /**
     * Bukkit's per-event handler list has no counterpart on Hytale, where the server registry already
     * keys consumers by class: an event's {@code getHandlerList} compiles here and answers null, and
     * nothing on this platform ever calls it.
     */
    public static Object getHandlerListOf(Class<? extends ECEvent> eventType) {
        return null;
    }

}
