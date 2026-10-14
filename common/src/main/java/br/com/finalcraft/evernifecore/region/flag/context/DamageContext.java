package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** Something about to be hurt. {@link #getActor()} is the attacker; {@link #getPlayer()} the player behind it, if any. */
public final class DamageContext extends ActionContext {

    private final Actor victim;

    /** @param position where the victim stands */
    public DamageContext(WorldLocPos position, Actor attacker, @Nullable Object nativeEvent, Actor victim) {
        super(EventSource.DAMAGE, position, attacker, nativeEvent);
        this.victim = Objects.requireNonNull(victim, "victim");
    }

    /** Who is about to be hurt. */
    public Actor getVictim() {
        return victim;
    }

    @Override
    public void close() {
        super.close();
        victim.close();
    }
}
