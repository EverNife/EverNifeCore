package br.com.finalcraft.evernifecore.math.game.vector.locpos;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.math.vector.Vec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import lombok.Getter;

@Getter
public class LocPos extends Vec3d<LocPos> {

    public LocPos(double x, double y, double z) {
        super(x, y, z);
    }

    @Override
    public LocPos at(double x, double y, double z) {
        return new LocPos(x, y, z);
    }

    // -- Factories ------------------------------------------------------------

    public static LocPos of(double x, double y, double z) {
        return new LocPos(x, y, z);
    }

    public static LocPos from(IVec3i<?> vec3i) {
        return of(vec3i.getX(), vec3i.getY(), vec3i.getZ());
    }

    public static LocPos from(IVec3d<?> vec3d) {
        return of(vec3d.getX(), vec3d.getY(), vec3d.getZ());
    }

    // -- Game conversions -----------------------------------------------------

    public BlockPos getBlockPos() {
        return new BlockPos(VecMath.floor_double(x), VecMath.floor_double(y), VecMath.floor_double(z));
    }

    public ChunkPos getChunkPos() {
        return getBlockPos().getChunkPos();
    }

    public RegionPos getRegionPos() {
        return getBlockPos().getRegionPos();
    }

    public MutableLocPos toMutable() {
        return new MutableLocPos(x, y, z);
    }

    public WorldLocPos atWorld(String worldName) {
        return WorldLocPos.of(x, y, z, worldName);
    }

    // -- Serialization --------------------------------------------------------

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return x + "|" + y + "|" + z;
    }

    public static LocPos deserialize(String string) {
        int p1 = string.indexOf('|');
        int p2 = string.indexOf('|', p1 + 1);

        double x = Double.parseDouble(string.substring(0, p1));
        double y = Double.parseDouble(string.substring(p1 + 1, p2));
        double z = Double.parseDouble(string.substring(p2 + 1, string.length()));

        return new LocPos(x, y, z);
    }

}
