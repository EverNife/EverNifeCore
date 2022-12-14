package br.com.finalcraft.evernifecore.minecraft.vector;

import br.com.finalcraft.evernifecore.minecraft.region.RegionPos;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

public class ChunkPos {
    protected final int x;
    protected final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static ChunkPos from(Chunk chunk){
        return new ChunkPos(chunk.getX(), chunk.getZ());
    }

    public static ChunkPos from(Location location){
        return BlockPos.from(location).getChunkPos();
    }

    public ChunkPos(BlockPos block) {
        this.x = block.getX() >> 4;
        this.z = block.getZ() >> 4;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int getXStart() {
        return this.x << 4;
    }

    public int getZStart() {
        return this.z << 4;
    }

    public int getXEnd() {
        return (this.x << 4) + 15;
    }

    public int getZEnd() {
        return (this.z << 4) + 15;
    }

    public BlockPos getBlock(int x, int y, int z) {
        return new BlockPos((this.x << 4) + x, y, (this.z << 4) + z);
    }

    public RegionPos getRegionPos(){
        return new RegionPos(this);
    }

    public Chunk getChunk(World world){
        return world.getChunkAt(this.x, this.z);
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
        return this.x + "|" + this.z;
    }

}
