package br.com.finalcraft.evernifecore.math.game.vector.region;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.vector.Vec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import lombok.Getter;

@Getter
public class RegionPos extends Vec2i<RegionPos> {

    public RegionPos(int x, int z) {
        super(x, z);
    }

    @Override
    public RegionPos at(int x, int z) {
        return new RegionPos(x, z);
    }

    // -- Factories ------------------------------------------------------------

    public static RegionPos of(int x, int z) {
        return new RegionPos(x, z);
    }

    public static RegionPos fromBlock(int blockX, int blockZ) {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return of(blockX >> shift, blockZ >> shift);
    }

    public static RegionPos fromBlock(IVec3d<?> iVec3d) {
        return fromBlock(VecMath.floor_double(iVec3d.getX()), VecMath.floor_double(iVec3d.getZ()));
    }

    public static RegionPos fromBlock(IVec3i<?> iVec3i) {
        return fromBlock(iVec3i.getX(), iVec3i.getZ());
    }

    public static RegionPos fromChunk(int chunkX, int chunkZ) {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        int chunkToRegionShift = options.getRegionShift() - options.getChunkShift();
        return new RegionPos(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift);
    }

    public static RegionPos fromChunk(Vec2i<?> vec2i) {
        return fromChunk(vec2i.getX(), vec2i.getZ());
    }

    // -- Grid info ------------------------------------------------------------

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

    // -- Conversions ----------------------------------------------------------

    public MutableRegionPos toMutable() {
        return new MutableRegionPos(x, z);
    }

    public WorldRegionPos atWorld(String worldName) {
        return new WorldRegionPos(x, z, worldName);
    }

    // -- Serialization --------------------------------------------------------

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return x + "|" + z;
    }

    public static RegionPos deserialize(String string) {
        int sep = string.indexOf('|');
        return new RegionPos(
                Integer.parseInt(string.substring(0, sep)),
                Integer.parseInt(string.substring(sep + 1))
        );
    }

}
