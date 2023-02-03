package br.com.finalcraft.evernifecore.minecraft.vector;

import com.google.common.collect.ComparisonChain;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

@Getter
@EqualsAndHashCode
public class LocPos implements Comparable<LocPos> {

    protected final double x;
    protected final double y;
    protected final double z;

    public LocPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static LocPos from(Location location){
        return new LocPos(location.getX(), location.getY(), location.getZ());
    }

    public static LocPos from(Entity entity){
        return from(entity.getLocation());
    }

    public LocPos setX(double x){
        return new LocPos(x, this.y, this.z);
    }

    public LocPos setY(double y){
        return new LocPos(this.x, y, this.z);
    }

    public LocPos setZ(double z){
        return new LocPos(this.x, this.y, z);
    }

    public LocPos add(double x, double y, double z){
        return new LocPos(this.x + x, this.y + y, this.z + z);
    }

    public LocPos add(BlockPos locPos){
        return new LocPos(this.x + locPos.x, this.y + locPos.y, this.z + locPos.z);
    }

    public LocPos add(LocPos... locPos){
        double x = this.x;
        double y = this.y;
        double z = this.z;
        for (LocPos blockPos1 : locPos) {
            x += blockPos1.x;
            y += blockPos1.y;
            z += blockPos1.z;
        }
        return new LocPos(x, y, z);
    }

    public LocPos subtract(double x, double y, double z){
        return new LocPos(this.x - x, this.y - y, this.z - z);
    }

    public LocPos subtract(BlockPos locPos){
        return new LocPos(this.x - locPos.x, this.y - locPos.y, this.z - locPos.z);
    }

    public LocPos subtract(LocPos... locPos){
        double x = this.x;
        double y = this.y;
        double z = this.z;
        for (LocPos blockPos1 : locPos) {
            x -= blockPos1.x;
            y -= blockPos1.y;
            z -= blockPos1.z;
        }
        return new LocPos(x, y, z);
    }

    public Location getLocation(World world){
        return new Location(world, this.x, this.y, this.z);
    }

    public Block getBlock(World world){
        return this.getBlockPos().getBlock(world);
    }

    public BlockPos getBlockPos(){
        return new BlockPos(this.x, this.y, this.z);
    }

    @Override
    public int compareTo(LocPos o) {
        return ComparisonChain.start().compare(this.y, o.y).compare(this.z, o.z).compare(this.x, o.x).result();
    }

    @Override
    public String toString() {
        return this.x + "|" + this.y + "|" + this.z;
    }

}
