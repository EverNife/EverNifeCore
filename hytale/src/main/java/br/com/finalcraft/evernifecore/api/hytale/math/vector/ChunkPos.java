package br.com.finalcraft.evernifecore.api.hytale.math.vector;

import br.com.finalcraft.evernifecore.api.common.math.vector.options.RegionGridOptions;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

public class ChunkPos {
    protected final int x;
    protected final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static ChunkPos at(WorldChunk chunk){
        return new ChunkPos(chunk.getX(), chunk.getZ());
    }

    public static ChunkPos at(Location location){
        return BlockPos.at(location).getChunkPos();
    }

    public static ChunkPos at(Vector3d vector3d){
        return BlockPos.at(vector3d.getX(), vector3d.getY(), vector3d.getZ()).getChunkPos();
    }

    public static ChunkPos at(Vector3i vector3i){
        return BlockPos.at(vector3i.getX(), vector3i.getY(), vector3i.getZ()).getChunkPos();
    }

    public ChunkPos(BlockPos block) {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        this.x = block.getX() >> options.getChunkShift();
        this.z = block.getZ() >> options.getChunkShift();
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int getXStart() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return this.x << options.getChunkShift();
    }

    public int getZStart() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return this.z << options.getChunkShift();
    }

    public int getXEnd() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return (this.x << options.getChunkShift()) + options.getChunkSize() - 1;
    }

    public int getZEnd() {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return (this.z << options.getChunkShift()) + options.getChunkSize() - 1;
    }

    public BlockPos getBlock(int x, int y, int z) {
        RegionGridOptions options = RegionGridOptions.getCurrent();
        return new BlockPos((this.x << options.getChunkShift()) + x, y, (this.z << options.getChunkShift()) + z);
    }

    public RegionPos getRegionPos(){
        return new RegionPos(this);
    }

    public WorldChunk getChunk(World world){
        return world.getChunk(indexChunkFromBlock());
    }

    public long indexChunkFromBlock(){
        return ChunkUtil.indexChunkFromBlock(this.x, this.z);
    }


    @Override
    public int hashCode(){
        int i = 1664525 * this.x + 1013904223;
        int j = 1664525 * (this.z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;

        ChunkPos chunkPos = (ChunkPos) o;

        if (this.x != chunkPos.x) return false;
        return this.z == chunkPos.z;
    }

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize(){
        return this.x + "|" + this.z;
    }

    public static ChunkPos deserialize(String serialized) {
        int sep = serialized.indexOf('|');
        int x = Integer.parseInt(serialized.substring(0, sep));
        int z = Integer.parseInt(serialized.substring(sep + 1));
        return new ChunkPos(x, z);
    }

}
