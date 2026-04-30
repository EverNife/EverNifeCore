package br.com.finalcraft.evernifecore.math.game.vector.blockpos;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.math.vector.Vec3i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Getter;

@Getter
public class WorldBlockPos extends Vec3i<WorldBlockPos> {

    protected final String worldName;

    public WorldBlockPos(int x, int y, int z, String worldName) {
        super(x, y, z);
        this.worldName = worldName;
    }

    @Override
    public WorldBlockPos at(int x, int y, int z) {
        return new WorldBlockPos(x, y, z, worldName);
    }

    // -- Factories ------------------------------------------------------------

    public static WorldBlockPos of(int x, int y, int z, String worldName) {
        return new WorldBlockPos(x, y, z, worldName);
    }

    public static WorldBlockPos from(IVec3i<?> vec3i, String worldName) {
        return of(vec3i.getX(), vec3i.getY(), vec3i.getZ(), worldName);
    }

    public static WorldBlockPos from(IVec3d<?> iVec3d,  String worldName) {
        return of(
                VecMath.floor_double(iVec3d.getX()),
                VecMath.floor_double(iVec3d.getY()),
                VecMath.floor_double(iVec3d.getZ()),
                worldName
        );
    }

    // -- Set ------------------------------------------------------------------

    public WorldBlockPos setWorldName(String worldName) {
        return new WorldBlockPos(x, y, z, worldName);
    }

    // -- Game conversions -----------------------------------------------------

    public BlockPos getBlockPos() {
        return BlockPos.of(x, y, z);
    }

    public LocPos getLocPos() {
        return LocPos.of(x, y, z);
    }

    public ChunkPos getChunkPos() {
        int shift = RegionGridOptions.getCurrent().getChunkShift();
        return ChunkPos.of(x >> shift, z >> shift);
    }

    public RegionPos getRegionPos() {
        int shift = RegionGridOptions.getCurrent().getRegionShift();
        return RegionPos.of(x >> shift, z >> shift);
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    public int compareTo(WorldBlockPos o) {
        int cmp = worldName.compareTo(o.worldName);
        if (cmp != 0) return cmp;
        if (y != o.y) return y - o.y;
        if (z != o.z) return z - o.z;
        return x - o.x;
    }

    @Override
    protected int calculateHashCode() {
        long worldNameHash = FCHashUtil.hash(worldName);
        return (int) FCHashUtil.hash(worldNameHash, x, y, z);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof WorldBlockPos other)){
            return false;
        }
        if (this == object){
            return true;
        }
        return this.worldName.equals(other.worldName) && x == other.getX() && y == other.getY() && z == other.getZ();
    }

    // -- Serialization --------------------------------------------------------

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return worldName + "|" + x + "|" + y + "|" + z;
    }

    public static WorldBlockPos deserialize(String string) {
        int p1 = string.indexOf('|');
        int p2 = string.indexOf('|', p1 + 1);
        int p3 = string.indexOf('|', p2 + 1);

        String worldName = string.substring(0, p1);
        int x = Integer.parseInt(string.substring(p1 + 1, p2));
        int y = Integer.parseInt(string.substring(p2 + 1, p3));
        int z = Integer.parseInt(string.substring(p3 + 1, string.length()));

        return new WorldBlockPos(x, y, z, worldName);
    }

}
