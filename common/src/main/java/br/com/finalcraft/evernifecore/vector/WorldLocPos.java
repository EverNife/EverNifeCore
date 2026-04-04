package br.com.finalcraft.evernifecore.vector;

import com.google.common.collect.ComparisonChain;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class WorldLocPos implements Comparable<WorldLocPos> {

    protected final double x;
    protected final double y;
    protected final double z;
    protected final String worldName;

    public WorldLocPos(double x, double y, double z, String worldName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldName = worldName;
    }

    public WorldLocPos setX(double x){
        return new WorldLocPos(x, this.y, this.z, worldName);
    }

    public WorldLocPos setY(double y){
        return new WorldLocPos(this.x, y, this.z, worldName);
    }

    public WorldLocPos setZ(double z){
        return new WorldLocPos(this.x, this.y, z, worldName);
    }

    public WorldLocPos add(double x, double y, double z){
        return new WorldLocPos(this.x + x, this.y + y, this.z + z, worldName);
    }

    public WorldLocPos add(BlockPos locPos){
        return new WorldLocPos(this.x + locPos.x, this.y + locPos.y, this.z + locPos.z, worldName);
    }

    public WorldLocPos add(WorldLocPos... locPos){
        double x = this.x;
        double y = this.y;
        double z = this.z;
        for (WorldLocPos blockPos1 : locPos) {
            x += blockPos1.x;
            y += blockPos1.y;
            z += blockPos1.z;
        }
        return new WorldLocPos(x, y, z, worldName);
    }

    public WorldLocPos subtract(double x, double y, double z){
        return new WorldLocPos(this.x - x, this.y - y, this.z - z, worldName);
    }

    public WorldLocPos subtract(BlockPos locPos){
        return new WorldLocPos(this.x - locPos.x, this.y - locPos.y, this.z - locPos.z, worldName);
    }

    public WorldLocPos subtract(WorldLocPos... locPos){
        double x = this.x;
        double y = this.y;
        double z = this.z;
        for (WorldLocPos blockPos1 : locPos) {
            x -= blockPos1.x;
            y -= blockPos1.y;
            z -= blockPos1.z;
        }
        return new WorldLocPos(x, y, z, worldName);
    }

    public BlockPos getBlockPos(){
        return new BlockPos(this.x, this.y, this.z);
    }

    @Override
    public int compareTo(WorldLocPos o) {
        return ComparisonChain.start().compare(this.y, o.y).compare(this.z, o.z).compare(this.x, o.x).result();
    }

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize(){
        return this.x + "|" + this.y + "|" + this.z + "|" + this.worldName;
    }

    public static WorldLocPos deserialize(String s) {
        int p1 = s.indexOf('|');
        int p2 = s.indexOf('|', p1 + 1);
        int p3 = s.indexOf('|', p2 + 1);

        double x = Double.parseDouble(s.substring(0, p1));
        double y = Double.parseDouble(s.substring(p1 + 1, p2));
        double z = Double.parseDouble(s.substring(p2 + 1, p3));
        String worldName = s.substring(p3 + 1);

        return new WorldLocPos(x, y, z, worldName);
    }
}
