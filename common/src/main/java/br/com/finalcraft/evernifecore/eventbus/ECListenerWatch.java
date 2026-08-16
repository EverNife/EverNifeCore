package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * The handle {@link ECEventBus#watchListeners} hands back: a producer keeps it to read the presence it
 * was last told about, and to stop following it. {@link #toString()} is one readable line - the types,
 * the owner and the presence - for a log or an operator's screen.
 */
public interface ECListenerWatch {

    /** The event types this watch follows, as they were given. */
    Collection<Class<? extends IECEvent>> getEventTypes();

    /** The plugin this watch belongs to - drained with it on shutdown - or {@code null} for nobody's. */
    @Nullable
    ECPluginData getPlugin();

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
