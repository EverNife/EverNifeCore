package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

/**
 * The handle {@link ECEventBus#subscribe} hands back, so a functional subscription can be cancelled
 * without the subscriber having to keep the bus, the type and the lambda around.
 */
public interface ECEventSubscription<T extends IECEvent> {

    /** The type this subscription was opened for. */
    Class<T> getEventType();

    /** Stops the delivery. Idempotent. */
    void unsubscribe();

    /** Whether events still reach this subscription - false once unsubscribed or drained. */
    boolean isActive();

}
