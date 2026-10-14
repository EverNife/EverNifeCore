package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** An entity used: a villager traded with, an item frame turned, a mount ridden. {@link #getActor()} is who uses it. */
public final class EntityContext extends ActionContext {

    private final Actor entity;

    /** @param position where the entity stands */
    public EntityContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, Actor entity) {
        super(EventSource.INTERACT, position, actor, nativeEvent);
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    /** The entity being used. */
    public Actor getEntity() {
        return entity;
    }

    @Override
    public void close() {
        super.close();
        entity.close();
    }
}
