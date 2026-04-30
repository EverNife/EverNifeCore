package br.com.finalcraft.evernifecore.math;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.MutableBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.MutableChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.WorldChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.MutableLocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.MutableRegionPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.WorldRegionPos;

public final class FCVectors {

    // -- BlockPos -------------------------------------------------------------

    public static BlockPos blockPos(int x, int y, int z) {
        return BlockPos.of(x, y, z);
    }

    public static MutableBlockPos mutableBlockPos(int x, int y, int z) {
        return MutableBlockPos.of(x, y, z);
    }

    public static WorldBlockPos worldBlockPos(int x, int y, int z, String worldName) {
        return WorldBlockPos.of(x, y, z, worldName);
    }

    // -- LocPos ---------------------------------------------------------------

    public static LocPos locPos(double x, double y, double z) {
        return LocPos.of(x, y, z);
    }

    public static MutableLocPos mutableLocPos(double x, double y, double z) {
        return MutableLocPos.of(x, y, z);
    }

    public static WorldLocPos worldLocPos(double x, double y, double z, String worldName) {
        return WorldLocPos.of(x, y, z, worldName);
    }

    // -- ChunkPos -------------------------------------------------------------

    public static ChunkPos chunkPos(int x, int z) {
        return ChunkPos.of(x, z);
    }

    public static MutableChunkPos mutableChunkPos(int x, int z) {
        return MutableChunkPos.of(x, z);
    }

    public static WorldChunkPos worldChunkPos(int x, int z, String worldName) {
        return WorldChunkPos.of(x, z, worldName);
    }

    // -- RegionPos ------------------------------------------------------------

    public static RegionPos regionPos(int x, int z) {
        return RegionPos.of(x, z);
    }

    public static MutableRegionPos mutableRegionPos(int x, int z) {
        return MutableRegionPos.of(x, z);
    }

    public static WorldRegionPos worldRegionPos(int x, int z, String worldName) {
        return WorldRegionPos.of(x, z, worldName);
    }

}
