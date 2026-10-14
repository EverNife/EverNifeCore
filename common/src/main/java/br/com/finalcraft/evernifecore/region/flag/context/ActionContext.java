package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * The action a protection flag is asked about: who does it ({@link #getActor()}), where, and the platform's event.
 * Each {@link EventSource} has its own subclass with the details (block, victim, spawned entity, movement);
 * {@link #as} reaches them.
 */
public abstract class ActionContext {

    private final EventSource source;
    private final WorldLocPos position;
    private final Actor actor;
    private final @Nullable Object nativeEvent;

    /** @param nativeEvent the platform's event; null when the action comes from no single event */
    protected ActionContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent) {
        this.source = Objects.requireNonNull(source, "source");
        this.position = Objects.requireNonNull(position, "position");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.nativeEvent = nativeEvent;
    }

    public EventSource getSource() {
        return source;
    }

    public String getWorld() {
        return position.getWorldName();
    }

    public WorldLocPos getPosition() {
        return position;
    }

    /** Who does the action: an entity, a block or the environment. */
    public Actor getActor() {
        return actor;
    }

    /** The player behind the action - {@code getActor().getRootPlayer()}; null when none. */
    public @Nullable FPlayer getPlayer() {
        return actor.getRootPlayer();
    }

    /**
     * This context as the subclass for its source.
     *
     * @throws IllegalArgumentException when it is another kind of context; check {@link #getSource()} first
     */
    public <C extends ActionContext> C as(Class<C> type) {
        if (!type.isInstance(this)) {
            throw new IllegalArgumentException("This " + source + " action carries a " + getClass().getSimpleName() + ", not a "
                    + type.getSimpleName() + ": check getSource() before asking for the details of another kind of action.");
        }
        return type.cast(this);
    }

    /** The platform's own event, for whoever needs more than this context carries; null when there was none. */
    public @Nullable Object getNativeEvent() {
        return nativeEvent;
    }

    /** The platform's own event as {@code type}; null when there was none or it is of another class. */
    public <E> @Nullable E getNativeEvent(Class<E> type) {
        return type.isInstance(nativeEvent) ? type.cast(nativeEvent) : null;
    }

    /** Internal: the dispatch of this action ended - the handles its actors made are closed. */
    public void close() {
        actor.close();
    }
}
