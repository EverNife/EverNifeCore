package br.com.finalcraft.evernifecore.math.game.vector.region;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.vector.MutableVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;

public class MutableRegionPos extends MutableVec2i<MutableRegionPos> {

    public MutableRegionPos(int x, int z) {
        super(x, z);
    }

    @Override
    public MutableRegionPos at(int x, int z) {
        this.x = x;
        this.z = z;
        this.hash = 0;
        return this;
    }

    // =========================================================================
    //  Factories
    // =========================================================================

    public static MutableRegionPos of(int x, int z) {
        return new MutableRegionPos(x, z);
    }

    public static MutableRegionPos from(IVec2i<?> other) {
        return new MutableRegionPos(other.getX(), other.getZ());
    }

    public static MutableRegionPos fromChunk(int chunkX, int chunkZ) {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        int chunkToRegionShift = options.getRegionShift() - options.getChunkShift();
        return new MutableRegionPos(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift);
    }

    public static MutableRegionPos fromBlock(int blockX, int blockZ) {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return new MutableRegionPos(blockX >> shift, blockZ >> shift);
    }

    public static MutableRegionPos fromBlock(IVec3d<?> iVec3d) {
        return fromBlock(VecMath.floor_double(iVec3d.getX()), VecMath.floor_double(iVec3d.getZ()));
    }

    public static MutableRegionPos fromBlock(IVec3i<?> iVec3i) {
        return fromBlock(iVec3i.getX(), iVec3i.getZ());
    }

    // =========================================================================
    //  Grid Info
    // =========================================================================

    public int getXStart() {
        return x << RegionGridOptions.getCurrent().getRegionShift();
    }

    public int getZStart() {
        return z << RegionGridOptions.getCurrent().getRegionShift();
    }

    public int getXEnd() {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return (x << shift) + (1 << shift) - 1;
    }

    public int getZEnd() {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return (z << shift) + (1 << shift) - 1;
    }

    public BlockPos getMinBlockPos() {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return BlockPos.of(x << shift, 0, z << shift);
    }

    public ChunkPos getMinChunkPos() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        int chunkToRegionShift = options.getRegionShift() - options.getChunkShift();
        return ChunkPos.of(x << chunkToRegionShift, z << chunkToRegionShift);
    }

    // =========================================================================
    //  Conversions
    // =========================================================================

    public RegionPos toImmutable() {
        return new RegionPos(x, z);
    }

    public WorldRegionPos atWorld(String worldName) {
        return new WorldRegionPos(x, z, worldName);
    }

    // =========================================================================
    //  Serialization
    // =========================================================================

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return x + "|" + z;
    }

    public static MutableRegionPos deserialize(String string) {
        int sep = string.indexOf('|');
        return new MutableRegionPos(
                Integer.parseInt(string.substring(0, sep)),
                Integer.parseInt(string.substring(sep + 1))
        );
    }

}
