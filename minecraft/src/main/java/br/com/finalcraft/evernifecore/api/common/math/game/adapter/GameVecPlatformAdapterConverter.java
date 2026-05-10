package br.com.finalcraft.evernifecore.api.common.math.game.adapter;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

public class GameVecPlatformAdapterConverter {

    public static GameVecPlatformAdapterConverter INSTANCE = new GameVecPlatformAdapterConverter();

    public BlockPos getBlockPos(Location location){
        return BlockPos.of(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public LocPos getLocPos(Location location){
        return LocPos.of(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public WorldLocPos getWorldLocPos(Location location){
        return WorldLocPos.of(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getName());
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
            return new Location(world, this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptBlockPosWorld extends AdaptBlockPos {

        private final String worldName;

        public AdaptBlockPosWorld(IVec3i iVec3i, String worldName) {
            super(iVec3i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }

        public Location getLocation() {
            return this.getLocation(getWorld());
        }
    }

    public static class AdaptLocPos extends BasePosAdapter<IVec3d> {

        public AdaptLocPos(IVec3d iVec3d) {
            super(iVec3d);
        }

        public Location getLocation(World world) {
            return new Location(world, this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptLocPosWorld extends AdaptLocPos {

        private final String worldName;

        public AdaptLocPosWorld(IVec3d iVec3d, String worldName) {
            super(iVec3d);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }

        public Location getLocation() {
            return this.getLocation(getWorld());
        }
    }

    public static class AdaptChunkPos extends BasePosAdapter<IVec2i> {

        public AdaptChunkPos(IVec2i iVec2i) {
            super(iVec2i);
        }

        public Chunk getChunk(World world) {
            return world.getChunkAt(this.getDelegate().getX(), this.getDelegate().getZ());
        }

    }

    public static class AdaptChunkPosWorld extends AdaptChunkPos {

        private final String worldName;

        public AdaptChunkPosWorld(IVec2i iVec2i, String worldName) {
            super(iVec2i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }

        public Chunk getChunk() {
            return getChunk(getWorld());
        }
    }
}
