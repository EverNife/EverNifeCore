package br.com.finalcraft.evernifecore.api.hytale.math.vector;

import com.google.common.collect.ComparisonChain;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import lombok.EqualsAndHashCode;
import lombok.Getter;

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

    public static LocPos at(Location location){
        Vector3d position = location.getPosition();
        return new LocPos(position.getX(), position.getY(), position.getZ());
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
        return new Location(world.getName(), this.x, this.y, this.z);
    }

    public BlockType getBlock(World world){
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
        return serialize();
    }

    public String serialize(){
        return this.x + "|" + this.y + "|" + this.z;
    }

    public static LocPos deserialize(String s) {
        int p1 = s.indexOf('|');
        int p2 = s.indexOf('|', p1 + 1);

        double x = Double.parseDouble(s.substring(0, p1));
        double y = Double.parseDouble(s.substring(p1 + 1, p2));
        double z = Double.parseDouble(s.substring(p2 + 1));

        return new LocPos(x, y, z);
    }

}
