package br.com.finalcraft.evernifecore.math.game.vector.chunkpos;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.math.vector.MutableVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.minecraft.math.game.adapter.MCGameVecAdapter;

public class MutableChunkPos extends MutableVec2i<MutableChunkPos> {

    public MutableChunkPos(int x, int z) {
        super(x, z);
    }

    @Override
    public MutableChunkPos at(int x, int z) {
        this.x = x;
        this.z = z;
        this.hash = 0;
        return this;
    }

    // =========================================================================
    //  Factories
    // =========================================================================

    public static MutableChunkPos of(int x, int z) {
        return new MutableChunkPos(x, z);
    }

    public static MutableChunkPos from(IVec2i<?> other) {
        return new MutableChunkPos(other.getX(), other.getZ());
    }

    public static MutableChunkPos fromBlock(int blockX, int blockZ) {
        int chunkShift = RegionGridOptions.getCurrent().getChunkShift();
        return new MutableChunkPos(blockX >> chunkShift, blockZ >> chunkShift);
    }

    public static MutableChunkPos fromBlock(IVec3d<?> iVec3d) {
        return fromBlock(VecMath.floor_double(iVec3d.getX()), VecMath.floor_double(iVec3d.getZ()));
    }

    public static MutableChunkPos fromBlock(IVec3i<?> iVec3i) {
        return fromBlock(iVec3i.getX(), iVec3i.getZ());
    }

    // =========================================================================
    //  Grid Info
    // =========================================================================

    public int getXStart() {
        return x << RegionGridOptions.getCurrent().getChunkShift();
    }

    public int getZStart() {
        return z << RegionGridOptions.getCurrent().getChunkShift();
    }

    public int getXEnd() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return (x << options.getChunkShift()) + options.getChunkSize() - 1;
    }

    public int getZEnd() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return (z << options.getChunkShift()) + options.getChunkSize() - 1;
    }

    public BlockPos getBlock(int x, int y, int z) {
        int shift = RegionGridOptions.getCurrent().getChunkShift();
        return BlockPos.of((this.x << shift) + x, y, (this.z << shift) + z);
    }

    // =========================================================================
    //  Adapters
    // =========================================================================

    public MCGameVecAdapter.AdaptChunkPos getMCAdapter(){
        return EverNifeCore.getPlatform().getVecAdapter().adaptChunkPos(this);
    }

    // =========================================================================
    //  Conversions
    // =========================================================================

    public RegionPos getRegionPos() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        int chunkToRegionShift = options.getRegionShift() - options.getChunkShift();
        return new RegionPos(x >> chunkToRegionShift, z >> chunkToRegionShift);
    }

    public ChunkPos toImmutable() {
        return new ChunkPos(x, z);
    }

    public WorldChunkPos atWorld(String worldName) {
        return new WorldChunkPos(x, z, worldName);
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

    public static MutableChunkPos deserialize(String string) {
        int p1 = string.indexOf('|');

        int x = Integer.parseInt(string.substring(0, p1));
        int z = Integer.parseInt(string.substring(p1 + 1, string.length()));

        return new MutableChunkPos(x, z);
    }

}
