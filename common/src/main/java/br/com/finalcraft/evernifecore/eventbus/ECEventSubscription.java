package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

/**
 * One subscription on an {@link ECEventBus}: the handle {@link ECEventBus#subscribe} hands back and
 * the view {@link ECEventBus#getSubscriptions()} lists - who is subscribed, to what, with which
 * {@link ECSubscribeOptions}. {@link #toString()} is one readable line of exactly that, for a log or
 * an operator's screen.
 */
public interface ECEventSubscription<T extends IECEvent> {

    /** The type this subscription named - not the concrete types it ends up hearing. */
    Class<T> getEventType();

    /** Priority, cancellation, exactness and owner, as they were asked for. */
    ECSubscribeOptions getOptions();

    /**
     * Who is subscribed: the {@link ECEventSubscriber} handed to {@code subscribe(...)}, or the
     * listener object {@link ECEventBus#register} scanned for an annotated method.
     */
    Object getSubscriber();

    /** Stops the delivery. Idempotent. */
    void unsubscribe();

    /** Whether events still reach this subscription - false once unsubscribed or drained. */
    boolean isActive();

}
