package br.com.finalcraft.evernifecore.minecraft.vector;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.minecraft.region.RegionPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

public class BlockPos implements Comparable<BlockPos>, Salvable {

    private int floor_double(double value){
        int i = (int) value;
        return value < (double)i ? i - 1 : i;
    }

    protected final int x;
    protected final int y;
    protected final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockPos(double x, double y, double z) {
        this.x = floor_double(x);
        this.y = floor_double(y);
        this.z = floor_double(z);
    }

    public static BlockPos from(Location location){
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos from(Entity entity){
        return from(entity.getLocation());
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockPos add(int x, int y, int z){
        return new BlockPos(this.x + x, this.y + y, this.z + z);
    }

    public Location getLocation(World world){
        return new Location(world, this.x, this.y, this.z);
    }

    public Block getBlock(World world){
        return world.getBlockAt(x,y,z);
    }

    public ChunkPos getChunkPos(){
        return new ChunkPos(this);
    }

    public RegionPos getRegionPos(){
        return new RegionPos(this);
    }

    @Override
    public int hashCode() { //Forge HashCode for BlockPost https://forums.minecraftforge.net/topic/88361-discussion-safe-to-use-blockposhashcode/
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPos)) return false;

        BlockPos blockPos = (BlockPos) o;

        if (this.x != blockPos.x) return false;
        if (this.y != blockPos.y) return false;
        return z == blockPos.z;
    }

    @Override
    public int compareTo(BlockPos o) {
        if (this.getY() == o.getY()) {
            return this.getZ() == o.getZ() ? this.getX() - o.getX() : this.getZ() - o.getZ();
        } else {
            return this.getY() - o.getY();
        }
    }

    @Override
    public String toString() {
        return this.x + "|" + this.y + "|" + this.z;
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue("x", x);
        section.setValue("y", y);
        section.setValue("z", z);
    }

    @Loadable
    public static BlockPos onConfigLoad(Config config, String path){
        int x = config.getInt(path + ".x");
        int y = config.getInt(path + ".y");
        int z = config.getInt(path + ".z");
        return new BlockPos(x,y,z);
    }

    public static class MutableBlockPos extends BlockPos {
        protected int x;
        protected int y;
        protected int z;

        public MutableBlockPos() {
            super(0, 0, 0);
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public int getZ() {
            return this.z;
        }

        public MutableBlockPos setPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public MutableBlockPos setX(int x) {
            this.x = x;
            return this;
        }

        public MutableBlockPos setY(int y) {
            this.y = y;
            return this;
        }

        public MutableBlockPos setZ(int z) {
            this.z = z;
            return this;
        }

        public BlockPos toImmutable() {
            return new BlockPos(x,y,z);
        }
    }
}
