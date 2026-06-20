package br.com.finalcraft.evernifecore.api.platoverride.math.game.adapter;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.joml.Vector3d;

public class GameVecPlatformAdapterConverter {

    public static GameVecPlatformAdapterConverter INSTANCE = new GameVecPlatformAdapterConverter();

    public BlockPos getBlockPos(Location location){
        Vector3d position = location.getPosition();
        return BlockPos.of(position.x(), position.y(), position.z());
    }

    public LocPos getLocPos(Location location){
        Vector3d position = location.getPosition();
        return LocPos.of(position.x(), position.y(), position.z());
    }

    public WorldLocPos getWorldLocPos(Location location){
        Vector3d position = location.getPosition();
        return WorldLocPos.of(position.x(), position.y(), position.z(), location.getWorld());
    }

    public static class BasePosAdapter<DELEGATE> {
        private final DELEGATE delegate;

        public BasePosAdapter(DELEGATE delegate) {
            this.delegate = delegate;
        }

        public DELEGATE getDelegate() {
            return delegate;
        }
    }

    public static class AdaptBlockPos extends BasePosAdapter<IVec3i> {

        public AdaptBlockPos(IVec3i iVec3i) {
            super(iVec3i);
        }

        public Location getLocation(World world) {
            return new Location(world.getName(), this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptBlockPosWorld extends AdaptBlockPos {

        private final String worldName;

        public AdaptBlockPosWorld(IVec3i iVec3i, String worldName) {
            super(iVec3i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Universe.get().getWorld(worldName);
        }

        public Location getLocation() {
            return new Location(worldName, this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptLocPos extends BasePosAdapter<IVec3d> {

        public AdaptLocPos(IVec3d iVec3d) {
            super(iVec3d);
        }

        public Location getLocation(World world) {
            return new Location(world.getName(), this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptLocPosWorld extends AdaptLocPos {

        private final String worldName;

        public AdaptLocPosWorld(IVec3d iVec3d, String worldName) {
            super(iVec3d);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Universe.get().getWorld(worldName);
        }

        public Location getLocation() {
            return this.getLocation(getWorld());
        }
    }

    public static class AdaptChunkPos extends BasePosAdapter<IVec2i> {

        public AdaptChunkPos(IVec2i iVec2i) {
            super(iVec2i);
        }

        public WorldChunk getChunk(World world) {
            return world.getChunk(ChunkUtil.indexChunkFromBlock(this.getDelegate().getX(), this.getDelegate().getZ()));
        }

    }

    public static class AdaptChunkPosWorld extends AdaptChunkPos {

        private final String worldName;

        public AdaptChunkPosWorld(IVec2i iVec2i, String worldName) {
            super(iVec2i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Universe.get().getWorld(worldName);
        }

        public WorldChunk getChunk() {
            return getChunk(getWorld());
        }
    }
}
