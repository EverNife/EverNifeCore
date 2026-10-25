package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Something about to be hurt ({@link EventSource#DAMAGE}) or just killed ({@link EventSource#KILL}).
 * {@link #getActor()} is the attacker - the killer of a kill; {@link #getPlayer()} the player behind it, if any: the
 * owner of a pet, the shooter of an arrow.
 */
public final class DamageContext extends ActionContext {

    private final Actor victim;

    /**
     * @param source   {@link EventSource#DAMAGE} or {@link EventSource#KILL}
     * @param position where the victim stands
     * @throws IllegalArgumentException for another source
     */
    public DamageContext(EventSource source, WorldLocPos position, Actor attacker, @Nullable Object nativeEvent, Actor victim) {
        super(source, position, attacker, nativeEvent);
        if (!source.equals(EventSource.DAMAGE) && !source.equals(EventSource.KILL)) {
            throw new IllegalArgumentException("A DamageContext is a DAMAGE or a KILL, not " + source
                    + ": pass the source of the event being asked about, or build the context that source carries.");
        }
        this.victim = Objects.requireNonNull(victim, "victim");
    }

    /** Who is about to be hurt, or who was killed. */
    public Actor getVictim() {
        return victim;
    }

    @Override
    public void close() {
        super.close();
        victim.close();
    }
}
