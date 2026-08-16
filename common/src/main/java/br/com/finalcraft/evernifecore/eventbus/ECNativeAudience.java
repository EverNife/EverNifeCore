package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

import java.util.Collections;
import java.util.List;

/**
 * One native event bus an {@link ECEventBus} mirrors into (bukkit, hytale, forge...).
 *
 * <p>Only the global bus mirrors, so an audience is only ever useful there; a scoped bus accepts one
 * and never calls it.</p>
 */
public interface ECNativeAudience {

    /** Stable id ("bukkit", "hytale", "forge") - the idempotency key for re-registration and the log tag. */
    String name();

    /**
     * Cheap and per class: {@link ECEventBus#post(IECEvent)} skips {@link #dispatch(IECEvent)} entirely
     * when false, and {@link ECEventBus#hasListeners(Class)} and the listener watches ask it too - before
     * any event exists, which is why it takes the class and not an instance. An audience that can see
     * its own native registrations calls {@link ECEventBus#refreshListenerWatches()} when they change;
     * one that cannot answers by polling, and says so in its documentation.
     */
    boolean hasListeners(Class<? extends IECEvent> eventType);

    /**
     * Delivers the SAME instance to this native bus. An implementation swallows and logs its own
     * errors: a broken audience never breaks the producer.
     */
    void dispatch(IECEvent event);

    /**
     * One readable line per native listener a post of {@code eventType} would reach - plugin, listener,
     * priority - for an operator asking who is there. Descriptive only: {@link #hasListeners(Class)}
     * stays the authority on presence, and an audience that cannot enumerate its side answers empty and
     * says so in its documentation.
     */
    default List<String> describeListeners(Class<? extends IECEvent> eventType) {
        return Collections.emptyList();
    }

}
