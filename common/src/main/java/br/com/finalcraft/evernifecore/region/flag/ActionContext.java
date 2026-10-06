package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * The action a protection flag is asked about. Each {@link EventSource} has its own subclass with the details
 * (block, victim, spawn type, movement); {@link #as} reaches them.
 */
public abstract class ActionContext {

    private final EventSource source;
    private final WorldLocPos position;
    private final @Nullable FPlayer player;
    private final Object nativeEvent;

    protected ActionContext(EventSource source, WorldLocPos position, @Nullable FPlayer player, Object nativeEvent) {
        this.source = Objects.requireNonNull(source, "source");
        this.position = Objects.requireNonNull(position, "position");
        this.player = player;
        this.nativeEvent = Objects.requireNonNull(nativeEvent, "nativeEvent");
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

    /** The player behind the action, traced to its root (a projectile's shooter, a tamed mob's owner); null when none. */
    public @Nullable FPlayer getPlayer() {
        return player;
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

    /** The platform's own event, for whoever needs more than this context carries. */
    public Object getNativeEvent() {
        return nativeEvent;
    }
}
