package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A player stepping from one chunk into another ({@link EventSource#EXPLORE}), in the platform's chunk size; both chunks
 * are in the world of {@link #getPosition()}.
 */
public final class ExploreContext extends ActionContext {

    private final ChunkPos from;
    private final ChunkPos to;

    /** @param position where the player arrived */
    public ExploreContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, ChunkPos from, ChunkPos to) {
        super(EventSource.EXPLORE, position, actor, nativeEvent);
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
    }

    /** The chunk left. */
    public ChunkPos getFrom() {
        return from;
    }

    /** The chunk entered. */
    public ChunkPos getTo() {
        return to;
    }
}
