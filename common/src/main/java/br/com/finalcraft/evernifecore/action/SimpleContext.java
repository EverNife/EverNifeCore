package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

/**
 * An action with no detail beyond who did it and where: a player respawning ({@link EventSource#RESPAWN}), one
 * starting or stopping flight ({@link EventSource#FLIGHT_TOGGLE}), or a plugin's own source that needs nothing more.
 */
public final class SimpleContext extends ActionContext {

    /** @param position where the actor stands */
    public SimpleContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent) {
        super(source, position, actor, nativeEvent);
    }
}
