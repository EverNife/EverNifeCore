package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;

public class RegionPos {

    protected final int x;
    protected final int z;

    public RegionPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public RegionPos(ChunkPos chunkPos) {
        this.x = chunkPos.getX() >> 5;
        this.z = chunkPos.getZ() >> 5;
    }

    public RegionPos(BlockPos blockPos) {
        this.x = blockPos.getX() >> 9;
        this.z = blockPos.getZ() >> 9;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public String toString() {
        return this.x + "|" + this.z;
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
        if (o == null || getClass() != o.getClass()) return false;

        RegionPos regionPos = (RegionPos) o;

        if (x != regionPos.x) return false;
        return z == regionPos.z;
    }
}
