package br.com.finalcraft.evernifecore.math.game.vector.blockpos;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.vecmath.VecMath;
import br.com.finalcraft.evernifecore.math.vector.MutableVec3i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.minecraft.math.game.adapter.MCGameVecAdapter;

public class MutableBlockPos extends MutableVec3i<MutableBlockPos> {

    public MutableBlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    @Override
    public MutableBlockPos at(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = 0;
        return this;
    }

    // -- Factories ------------------------------------------------------------

    public static MutableBlockPos of(int x, int y, int z) {
        return new MutableBlockPos(x, y, z);
    }

    public static MutableBlockPos from(IVec3i<?> other) {
        return of(other.getX(), other.getY(), other.getZ());
    }

    public static MutableBlockPos from(IVec3d<?> iVec3d) {
        return of(
                VecMath.floor_double(iVec3d.getX()),
                VecMath.floor_double(iVec3d.getY()),
                VecMath.floor_double(iVec3d.getZ())
        );
    }

    // -- Adapters -----------------------------------------------------

    public MCGameVecAdapter.AdaptBlockPos getMinecraftAdapter(){
        return EverNifeCore.getPlatform().getVecAdapter().adaptBlockPos(this);
    }

    // -- Conversions -----------------------------------------------------

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

    public WorldBlockPos atWorld(String worldName) {
        return WorldBlockPos.of(x, y, z, worldName);
    }

    public BlockPos toImmutable() {
        return new BlockPos(x, y, z);
    }

    // -- Serialization --------------------------------------------------------

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return x + "|" + y + "|" + z;
    }

    public static MutableBlockPos deserialize(String string) {
        int p1 = string.indexOf('|');
        int p2 = string.indexOf('|', p1 + 1);

        int x = Integer.parseInt(string.substring(0, p1));
        int y = Integer.parseInt(string.substring(p1 + 1, p2));
        int z = Integer.parseInt(string.substring(p2 + 1, string.length()));

        return new MutableBlockPos(x, y, z);
    }

}
