package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;

import java.util.Objects;

/**
 * One block and the value stored for it: the immutable element a range query answers with. The value is the
 * one held when the query snapshotted the chunk, not a live view of it.
 *
 * @param <O> the block-value type of the owning manager
 */
public final class BlockRecord<O> {

    private final String world;
    private final BlockPos pos;
    private final O value;

    public BlockRecord(String world, BlockPos pos, O value) {
        this.world = world;
        this.pos = pos;
        this.value = value;
    }

    public static <O> BlockRecord<O> of(String world, BlockPos pos, O value) {
        return new BlockRecord<>(world, pos, value);
    }

    public String getWorld() {
        return world;
    }

    public BlockPos getPos() {
        return pos;
    }

    public O getValue() {
        return value;
    }

    /** The position and its world as a single value, for an API that takes a {@link WorldBlockPos}. */
    public WorldBlockPos getWorldPos() {
        return pos.atWorld(world);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockRecord)) {
            return false;
        }
        BlockRecord<?> that = (BlockRecord<?>) other;
        return Objects.equals(world, that.world)
                && Objects.equals(pos, that.pos)
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, pos, value);
    }

    @Override
    public String toString() {
        return "BlockRecord{" + world + " " + pos + " -> " + value + "}";
    }
}
