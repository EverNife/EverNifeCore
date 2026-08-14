package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

/**
 * One native event bus an {@link ECEventBus} mirrors into (bukkit, hytale, forge...).
 *
 * <p>Only the global bus mirrors, so an audience is only ever useful there; a scoped bus accepts one
 * and never calls it.</p>
 */
public interface ECNativeAudience {

    /** Stable id ("bukkit", "hytale", "forge") - the idempotency key for re-registration and the log tag. */
    String name();

    /** Cheap check: {@link ECEventBus#post(IECEvent)} skips {@link #dispatch(IECEvent)} entirely when false. */
    boolean hasListeners(IECEvent event);

    /**
     * Delivers the SAME instance to this native bus. An implementation swallows and logs its own
     * errors: a broken audience never breaks the producer.
     */
    void dispatch(IECEvent event);

}
