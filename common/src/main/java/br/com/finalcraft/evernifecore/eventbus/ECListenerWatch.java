package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

import java.util.Collection;

/**
 * The handle {@link ECEventBus#watchListeners} hands back: a producer keeps it to read the presence it
 * was last told about, and to stop following it.
 */
public interface ECListenerWatch {

    /** The event types this watch follows, as they were given. */
    Collection<Class<? extends IECEvent>> getEventTypes();

    /**
     * The presence as of the last evaluation - {@code true} between the {@code onFirstListener} and the
     * {@code onLastListenerGone} callbacks, {@code false} outside them.
     */
    boolean hasListeners();

    /** Whether this watch still follows the bus - false once stopped or drained with its plugin. */
    boolean isActive();

    /** Detaches the watch. No callback fires for it afterwards, whatever the presence does. Idempotent. */
    void stop();

}
