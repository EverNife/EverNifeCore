package br.com.finalcraft.evernifecore.minecraft.vector;

import br.com.finalcraft.evernifecore.minecraft.region.RegionPos;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

@Getter
@EqualsAndHashCode
public class BlockPos implements Comparable<BlockPos> {

    private static int floor_double(double value){
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
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

    public BlockPos setX(int x){
        return new BlockPos(x, this.y, this.z);
    }

    public BlockPos setY(int y){
        return new BlockPos(this.x, y, this.z);
    }

    public BlockPos setZ(int z){
        return new BlockPos(this.x, this.y, z);
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

}
