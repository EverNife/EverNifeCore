package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * An action a player, a mob or the environment took in the world, as the platform translated it - one subclass per
 * {@link EventSource} ({@link ActionEvents} is the catalog), each with a cancellable {@code Pre} posted before the outcome,
 * after the regions allowed it, and a {@code Post} posted after it, never for a cancelled native event.
 *
 * <p>Built only while somebody listens, and the platform's native listener for the source is on the server only while
 * somebody listens: subscribe to the class of the source you need. Subscribing here hears every source and so switches
 * every one of them on - the liquid flow included, the costliest event a server raises.</p>
 *
 * <p>Posted on the thread of the native event (the main thread on Bukkit, the world's thread on Hytale). The context's
 * native handles are valid during the post only: read what you need, keep the UUID or the position, not the handle.
 * A native Bukkit listener names {@code Pre} or {@code Post}, which carry the handler lists; the bus hears an outer class
 * or this base as the whole family.</p>
 */
public abstract class ActionEvent<C extends ActionContext> extends ECEvent implements IECEvent {

    private final C context;

    /** @throws IllegalArgumentException when {@code context} is of another source than {@code source} */
    protected ActionEvent(EventSource source, C context) {
        this.context = Objects.requireNonNull(context, "context");
        if (!context.getSource().equals(source)) {
            throw new IllegalArgumentException("A " + getClass().getName() + " carries a " + source + " action, not a "
                    + context.getSource() + " one: build the context with the source of the event you post.");
        }
    }

    public C getContext() {
        return context;
    }

    public EventSource getSource() {
        return context.getSource();
    }

    /** Who did it: an entity, a block or the environment. */
    public Actor getActor() {
        return context.getActor();
    }

    /** The player behind the action; null for a mob or the environment. */
    public @Nullable FPlayer getPlayer() {
        return context.getPlayer();
    }
}
