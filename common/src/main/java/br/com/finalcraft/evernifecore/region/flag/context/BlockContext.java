package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** A block broken, placed or used - by a player, a mob, a machine, the environment or an explosion. */
public final class BlockContext extends ActionContext {

    private final TypeKey blockKey;
    private final @Nullable TypeKey itemInHandKey;

    /**
     * @param source        {@link EventSource#BREAK}, {@link EventSource#PLACE} or {@link EventSource#INTERACT}
     * @param blockKey      what the block is, e.g. {@code minecraft:sand}
     * @param itemInHandKey what the actor holds; null for an empty hand or no hand at all
     */
    public BlockContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent,
                        TypeKey blockKey, @Nullable TypeKey itemInHandKey) {
        super(source, position, actor, nativeEvent);
        this.blockKey = Objects.requireNonNull(blockKey, "blockKey");
        this.itemInHandKey = itemInHandKey;
    }

    public TypeKey getBlockKey() {
        return blockKey;
    }

    public @Nullable TypeKey getItemInHandKey() {
        return itemInHandKey;
    }
}
