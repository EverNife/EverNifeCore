package br.com.finalcraft.evernifecore.math.game.vector.chunkpos;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.math.vector.Vec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.minecraft.math.game.adapter.MCGameVecAdapter;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Getter;

@Getter
public class WorldChunkPos extends Vec2i<WorldChunkPos> {

    protected final String worldName;

    public WorldChunkPos(int x, int z, String worldName) {
        super(x, z);
        this.worldName = worldName;
    }

    @Override
    public WorldChunkPos at(int x, int z) {
        return new WorldChunkPos(x, z, this.worldName);
    }

    // -- Factories ------------------------------------------------------------

    public static WorldChunkPos of(int x, int z, String worldName) {
        return new WorldChunkPos(x, z, worldName);
    }

    public static WorldChunkPos from(IVec2i<?> vec2i, String worldName) {
        return of(vec2i.getX(), vec2i.getZ(), worldName);
    }

    public static WorldChunkPos fromBlock(int blockX, int blockZ, String worldName) {
        int chunkShift = RegionGridOptions.getCurrent().getChunkShift();
        return new WorldChunkPos(blockX >> chunkShift, blockZ >> chunkShift, worldName);
    }

    public static WorldChunkPos fromBlock(IVec3d<?> iVec3d, String worldName) {
        return fromBlock(VecMath.floor_double(iVec3d.getX()), VecMath.floor_double(iVec3d.getZ()), worldName);
    }

    public static WorldChunkPos fromBlock(IVec3i<?> iVec3i, String worldName) {
        return fromBlock(iVec3i.getX(), iVec3i.getZ(), worldName);
    }

    // -- Set ------------------------------------------------------------------

    public WorldChunkPos setWorldName(String worldName) {
        return new WorldChunkPos(x, z, worldName);
    }

    // -- Grid info ------------------------------------------------------------

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

    // -- Adapters -----------------------------------------------------

    public MCGameVecAdapter.AdaptChunkPosWorld getMCAdapter(){
        return EverNifeCore.getPlatform().getVecAdapter().adaptChunkPosWorld(this);
    }

    // -- Conversions ----------------------------------------------------------

    public ChunkPos getChunkPos() {
        return ChunkPos.of(x, z);
    }

    public RegionPos getRegionPos() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        int chunkToRegionShift = options.getRegionShift() - options.getChunkShift();
        return new RegionPos(x >> chunkToRegionShift, z >> chunkToRegionShift);
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    public int compareTo(WorldChunkPos o) {
        int cmp = worldName.compareTo(o.worldName);
        if (cmp != 0) return cmp;
        if (z != o.z) return z - o.z;
        return x - o.x;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof WorldChunkPos other)) {
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

    public static WorldChunkPos deserialize(String string) {
        int p1 = string.indexOf('|');
        int p2 = string.indexOf('|', p1 + 1);

        String worldName = string.substring(0, p1);
        int x = Integer.parseInt(string.substring(p1 + 1, p2));
        int z = Integer.parseInt(string.substring(p2 + 1, string.length()));

        return new WorldChunkPos(x, z, worldName);
    }

}
