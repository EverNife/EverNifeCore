package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A player about to cross the border of one region. Built by the movement tracker for each region entered or
 * left, so the flags of that region speak about that crossing only; the platform's move event, when there is one,
 * is the same for every crossing of that move.
 */
public final class MoveContext extends ActionContext {

    public enum Transition {
        ENTER, LEAVE
    }

    private final WorldLocPos from;
    private final MoveCause cause;
    private final Transition transition;
    private final Region region;

    /** @param nativeEvent the platform's move event; null for a move no single event caused */
    public MoveContext(FPlayer player, WorldLocPos from, WorldLocPos to, MoveCause cause, Transition transition, Region region,
                       @Nullable Object nativeEvent) {
        super(EventSource.MOVE, to, Actor.player(player, null), nativeEvent);
        this.from = Objects.requireNonNull(from, "from");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.transition = Objects.requireNonNull(transition, "transition");
        this.region = Objects.requireNonNull(region, "region");
    }

    public WorldLocPos getFrom() {
        return from;
    }

    /** Where the player is going; the same as {@link #getPosition()}. */
    public WorldLocPos getTo() {
        return getPosition();
    }

    public MoveCause getCause() {
        return cause;
    }

    public Transition getTransition() {
        return transition;
    }

    /** The region being entered or left. */
    public Region getRegion() {
        return region;
    }
}
