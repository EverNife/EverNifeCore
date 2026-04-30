package br.com.finalcraft.evernifecore.math.game.vector.region;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.vector.Vec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Getter;

@Getter
public class WorldRegionPos extends Vec2i<WorldRegionPos> {

    protected final String worldName;

    public WorldRegionPos(int x, int z, String worldName) {
        super(x, z);
        this.worldName = worldName;
    }

    @Override
    public WorldRegionPos at(int x, int z) {
        return new WorldRegionPos(x, z, worldName);
    }

    // -- Factories ------------------------------------------------------------

    public static WorldRegionPos of(int x, int z, String worldName) {
        return new WorldRegionPos(x, z, worldName);
    }

    public static WorldRegionPos from(IVec2i<?> vec2i, String worldName) {
        return of(vec2i.getX(), vec2i.getZ(), worldName);
    }

    public static WorldRegionPos fromChunk(int chunkX, int chunkZ, String worldName) {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        int chunkToRegionShift = options.getRegionShift() - options.getChunkShift();
        return new WorldRegionPos(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift, worldName);
    }

    public static WorldRegionPos fromBlock(int blockX, int blockZ, String worldName) {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return new WorldRegionPos(blockX >> shift, blockZ >> shift, worldName);
    }

    public static WorldRegionPos fromBlock(IVec3d<?> iVec3d, String worldName) {
        return fromBlock(VecMath.floor_double(iVec3d.getX()), VecMath.floor_double(iVec3d.getZ()), worldName);
    }

    public static WorldRegionPos fromBlock(IVec3i<?> iVec3i, String worldName) {
        return fromBlock(iVec3i.getX(), iVec3i.getZ(), worldName);
    }

    // -- Set ------------------------------------------------------------------

    public WorldRegionPos setWorldName(String worldName) {
        return new WorldRegionPos(x, z, worldName);
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

    public RegionPos getRegionPos() {
        return RegionPos.of(x, z);
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    public int compareTo(WorldRegionPos o) {
        int cmp = worldName.compareTo(o.worldName);
        if (cmp != 0) return cmp;
        if (z != o.z) return z - o.z;
        return x - o.x;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof WorldRegionPos other)) {
            return false;
        }
        if (this == object) {
            return true;
        }
        return this.worldName.equals(other.worldName) && x == other.x && z == other.z;
    }

    @Override
    protected int calculateHashCode() {
        long worldNameHash = FCHashUtil.hash(worldName);
        return (int) FCHashUtil.hash(worldNameHash, x, z);
    }

    // -- Serialization --------------------------------------------------------

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return worldName + "|" + x + "|" + z;
    }

    public static WorldRegionPos deserialize(String string) {
        int p1 = string.indexOf('|');
        int p2 = string.indexOf('|', p1 + 1);

        String worldName = string.substring(0, p1);
        int x = Integer.parseInt(string.substring(p1 + 1, p2));
        int z = Integer.parseInt(string.substring(p2 + 1, string.length()));

        return new WorldRegionPos(x, z, worldName);
    }

}
