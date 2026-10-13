package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** A block broken, placed or used. */
public final class BlockContext extends ActionContext {

    private final String blockId;
    private final @Nullable String itemInHandId;
    private final boolean fakePlayer;

    /**
     * @param source       {@link EventSource#BREAK}, {@link EventSource#PLACE} or {@link EventSource#INTERACT}
     * @param blockId      the platform-neutral id of the block, e.g. {@code minecraft:sand}
     * @param itemInHandId the id of what the player holds; null for an empty hand or no player
     * @param fakePlayer   whether the actor is a machine posing as a player
     */
    public BlockContext(EventSource source, WorldLocPos position, @Nullable FPlayer player, @Nullable Object nativeEvent,
                        String blockId, @Nullable String itemInHandId, boolean fakePlayer) {
        super(source, position, player, nativeEvent);
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.itemInHandId = itemInHandId;
        this.fakePlayer = fakePlayer;
    }

    public String getBlockId() {
        return blockId;
    }

    public @Nullable String getItemInHandId() {
        return itemInHandId;
    }

    public boolean isFakePlayer() {
        return fakePlayer;
    }
}
