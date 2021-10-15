package br.com.finalcraft.evernifecore.minecraft.vector;

import br.com.finalcraft.evernifecore.config.Config;
import org.bukkit.Chunk;

public class ChunkPos implements Config.Salvable {
    public final int x;
    public final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static ChunkPos from(Chunk chunk){
        return new ChunkPos(chunk.getX(), chunk.getZ());
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

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ChunkPos)) {
            return false;
        } else {
            ChunkPos chunkPos = (ChunkPos)o;
            return this.x == chunkPos.x && this.z == chunkPos.z;
        }
    }

    public BlockPos getBlock(int x, int y, int z) {
        return new BlockPos((this.x << 4) + x, y, (this.z << 4) + z);
    }

    @Override
    public void onConfigSave(Config config, String path) {
        config.setValue(path + ".chunkX", x);
        config.setValue(path + ".chunkZ", z);
    }

    @Config.Loadable
    public static ChunkPos onConsigLoad(Config config, String path){
        int x = config.getInt(path + ".x");
        int z = config.getInt(path + ".z");
        return new ChunkPos(x,z);
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

}
