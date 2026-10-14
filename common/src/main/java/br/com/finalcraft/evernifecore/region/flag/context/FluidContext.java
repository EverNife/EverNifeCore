package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A liquid about to flow from one block into the next ({@link EventSource#FLUID}). {@link #getPosition()} is where it
 * would arrive, {@link #getFrom()} where it comes from; the actor is the liquid itself, as the environment.
 */
public final class FluidContext extends ActionContext {

    private final WorldLocPos from;

    /**
     * @param to    the block the liquid would flow into
     * @param actor the liquid, e.g. {@code minecraft:water} as {@link Actor.Type#ENVIRONMENT}
     * @param from  the block it flows out of
     */
    public FluidContext(WorldLocPos to, Actor actor, @Nullable Object nativeEvent, WorldLocPos from) {
        super(EventSource.FLUID, to, actor, nativeEvent);
        this.from = Objects.requireNonNull(from, "from");
    }

    /** The block the liquid flows out of. */
    public WorldLocPos getFrom() {
        return from;
    }
}
