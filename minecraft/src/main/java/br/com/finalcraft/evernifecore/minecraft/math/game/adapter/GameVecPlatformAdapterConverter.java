package br.com.finalcraft.evernifecore.minecraft.math.game.adapter;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.math.vector.Vec2i;
import br.com.finalcraft.evernifecore.math.vector.Vec3d;
import br.com.finalcraft.evernifecore.math.vector.Vec3i;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

public class GameVecPlatformAdapterConverter {

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


    public static class AdaptBlockPos extends BasePosAdapter<Vec3i> {

        public AdaptBlockPos(Vec3i vec3i) {
            super(vec3i);
        }

        public Location getLocation(World world) {
            return new Location(world, this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptBlockPosWorld extends AdaptBlockPos {

        private final String worldName;

        public AdaptBlockPosWorld(Vec3i vec3i, String worldName) {
            super(vec3i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }

        public Location getLocation() {
            return this.getLocation(getWorld());
        }
    }

    public static class AdaptLocPos extends BasePosAdapter<Vec3d> {

        public AdaptLocPos(Vec3d vec3d) {
            super(vec3d);
        }

        public Location getLocation(World world) {
            return new Location(world, this.getDelegate().getX(), this.getDelegate().getY(), this.getDelegate().getZ());
        }
    }

    public static class AdaptLocPosWorld extends AdaptLocPos {

        private final String worldName;

        public AdaptLocPosWorld(Vec3d vec3i, String worldName) {
            super(vec3i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }

        public Location getLocation() {
            return this.getLocation(getWorld());
        }
    }

    public static class AdaptChunkPos extends BasePosAdapter<Vec2i> {

        public AdaptChunkPos(Vec2i vec2i) {
            super(vec2i);
        }

        public Chunk getChunk(World world) {
            return world.getChunkAt(this.getDelegate().getX(), this.getDelegate().getZ());
        }

    }

    public static class AdaptChunkPosWorld extends AdaptChunkPos {

        private final String worldName;

        public AdaptChunkPosWorld(Vec2i vec2i, String worldName) {
            super(vec2i);
            this.worldName = worldName;
        }

        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }

        public Chunk getChunk(World world) {
            return getChunk(getWorld());
        }
    }
}
