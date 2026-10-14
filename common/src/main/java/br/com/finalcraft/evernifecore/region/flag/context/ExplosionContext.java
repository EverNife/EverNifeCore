package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

/**
 * An explosion about to go off, asked as a whole: a denial cancels it. What it breaks is then asked as
 * {@link EventSource#BREAK} and whom it hurts as {@link EventSource#DAMAGE}, each with this same actor and inside the
 * same {@link br.com.finalcraft.evernifecore.region.RegionDispatch}.
 */
public final class ExplosionContext extends ActionContext {

    private final @Nullable Float strength;

    /**
     * @param position where it goes off
     * @param actor    what explodes - a creeper, a TNT, a bed
     * @param strength its power when the platform tells it; null otherwise
     */
    public ExplosionContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, @Nullable Float strength) {
        super(EventSource.EXPLOSION, position, actor, nativeEvent);
        this.strength = strength;
    }

    public @Nullable Float getStrength() {
        return strength;
    }
}
