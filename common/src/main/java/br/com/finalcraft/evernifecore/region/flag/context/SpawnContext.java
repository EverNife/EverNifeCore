package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A creature about to spawn. Which spawns are asked about at all (eggs, spawners) is the platform's choice; a natural
 * spawn's actor is {@code environment:spawn}.
 */
public final class SpawnContext extends ActionContext {

    private final Actor entity;
    private final String reason;

    /**
     * @param actor  what spawns it - {@code environment:spawn} for a natural spawn
     * @param reason the platform's own spawn reason, as text
     */
    public SpawnContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, Actor entity, String reason) {
        super(EventSource.SPAWN, position, actor, nativeEvent);
        this.entity = Objects.requireNonNull(entity, "entity");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The creature about to spawn. */
    public Actor getEntity() {
        return entity;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public void close() {
        super.close();
        entity.close();
    }
}
