package br.com.finalcraft.evernifecore.region.terrain;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;

import java.util.List;

/**
 * A snapshot {@link ITerrainAccess#readSnapshot} opened. {@link RegionTerrain#restoreSnapshot} walks its box and puts
 * back, position by position, only what the region's shape holds; every call runs on the world's thread.
 */
public interface TerrainPlacement {

    /** The lowest corner of the box the snapshot covers. */
    BlockPos getMin();

    /** The highest corner of the box the snapshot covers. */
    BlockPos getMax();

    /** Puts back the block the snapshot kept at a position of its box - air where it kept none. */
    void placeBlock(int x, int y, int z);

    /** The block each entity the snapshot kept stands in; {@link #placeEntity} takes the same index. */
    List<BlockPos> getEntityPositions();

    /** Spawns again the {@code index}-th entity of {@link #getEntityPositions()}. */
    void placeEntity(int index);
}
