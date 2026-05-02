package br.com.finalcraft.evernifecore.math.game.vector.locpos;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.math.vector.Vec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.minecraft.math.game.adapter.MCGameVecAdapter;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Getter;

@Getter
public class WorldLocPos extends Vec3d<WorldLocPos> {

    protected final String worldName;

    public WorldLocPos(double x, double y, double z, String worldName) {
        super(x, y, z);
        this.worldName = worldName;
    }

    @Override
    public WorldLocPos at(double x, double y, double z) {
        return new WorldLocPos(x, y, z, worldName);
    }

    // -- Factories ------------------------------------------------------------

    public static WorldLocPos of(double x, double y, double z, String worldName) {
        return new WorldLocPos(x, y, z, worldName);
    }

    public static WorldLocPos from(IVec3i<?> vec3i, String worldName) {
        return of(vec3i.getX(), vec3i.getY(), vec3i.getZ(), worldName);
    }

    public static WorldLocPos from(IVec3d<?> vec3d, String worldName) {
        return of(vec3d.getX(), vec3d.getY(), vec3d.getZ(), worldName);
    }

    // -- Set ------------------------------------------------------------------

    public WorldLocPos setWorldName(String worldName) {
        return new WorldLocPos(x, y, z, worldName);
    }

    // -- Adapters -----------------------------------------------------

    public MCGameVecAdapter.AdaptLocPosWorld getMinecraftAdapter(){
        return EverNifeCore.getPlatform().getVecAdapter().adaptLocPosWorld(this);
    }

    // -- Game conversions -----------------------------------------------------

    public LocPos getLocPos() {
        return LocPos.of(x, y, z);
    }

    public BlockPos getBlockPos() {
        return new BlockPos(VecMath.floor_double(x), VecMath.floor_double(y), VecMath.floor_double(z));
    }

    public ChunkPos getChunkPos() {
        return getBlockPos().getChunkPos();
    }

    public RegionPos getRegionPos() {
        return getBlockPos().getRegionPos();
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    public int compareTo(WorldLocPos o) {
        int cmp = worldName.compareTo(o.worldName);
        if (cmp != 0) return cmp;
        if (y != o.y) return Double.compare(y, o.y);
        if (z != o.z) return Double.compare(z, o.z);
        return Double.compare(x, o.x);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof WorldLocPos other)) {
            return false;
        }
        if (this == object) {
            return true;
        }
        return this.worldName.equals(other.worldName) && x == other.getX() && y == other.getY() && z == other.getZ();
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            long worldNameHash = FCHashUtil.hash(worldName);
            this.hash = (int) FCHashUtil.hash(
                    worldNameHash,
                    Double.doubleToLongBits(x),
                    Double.doubleToLongBits(y),
                    Double.doubleToLongBits(z)
            );
            if (hash == 0) {
                hash = 1;
            }
        }
        return hash;
    }

    // -- Serialization --------------------------------------------------------

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return worldName + "|" + x + "|" + y + "|" + z;
    }

    public static WorldLocPos deserialize(String string) {
        int p1 = string.indexOf('|');
        int p2 = string.indexOf('|', p1 + 1);
        int p3 = string.indexOf('|', p2 + 1);

        String worldName = string.substring(0, p1);
        double x = Double.parseDouble(string.substring(p1 + 1, p2));
        double y = Double.parseDouble(string.substring(p2 + 1, p3));
        double z = Double.parseDouble(string.substring(p3 + 1, string.length()));

        return new WorldLocPos(x, y, z, worldName);
    }

}
