package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

/**
 * What a subscriber does with one event. Anything it throws - checked or not - goes to the bus's
 * {@link ECEventExceptionHandler}; the subscribers queued behind it still run.
 */
@FunctionalInterface
public interface ECEventSubscriber<T extends IECEvent> {

    void handle(T event) throws Throwable;

}
