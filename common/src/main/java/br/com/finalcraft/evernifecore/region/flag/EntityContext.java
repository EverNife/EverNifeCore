package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** An entity used: a villager traded with, an item frame turned, a mount ridden. */
public final class EntityContext extends ActionContext {

    private final String entityTypeId;
    private final @Nullable FPlayer entityPlayer;

    /**
     * @param position     where the entity stands
     * @param player       who used it; null for no player behind the action
     * @param entityTypeId the platform-neutral id of the entity's type, e.g. {@code minecraft:villager}
     * @param entityPlayer the entity when it is a player
     */
    public EntityContext(WorldLocPos position, @Nullable FPlayer player, @Nullable Object nativeEvent,
                         String entityTypeId, @Nullable FPlayer entityPlayer) {
        super(EventSource.INTERACT, position, player, nativeEvent);
        this.entityTypeId = Objects.requireNonNull(entityTypeId, "entityTypeId");
        this.entityPlayer = entityPlayer;
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public @Nullable FPlayer getEntityPlayer() {
        return entityPlayer;
    }
}
