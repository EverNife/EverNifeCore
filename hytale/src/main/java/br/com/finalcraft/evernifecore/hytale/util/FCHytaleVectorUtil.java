package br.com.finalcraft.evernifecore.hytale.util;

import br.com.finalcraft.evernifecore.vector.BlockPos;
import br.com.finalcraft.evernifecore.vector.ChunkPos;
import br.com.finalcraft.evernifecore.vector.LocPos;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

public class FCHytaleVectorUtil {

    // ---------------------------------------------
    //   BlockPos
    // ---------------------------------------------

    public static BlockPos blockPosAt(Vector3d vector3d){
        return BlockPos.at(vector3d.getX(), vector3d.getY(), vector3d.getZ());
    }

    public static BlockPos blockPosAt(Vector3i vector3i){
        return BlockPos.at(vector3i.getX(), vector3i.getY(), vector3i.getZ());
    }

    public static BlockPos blockPosAt(Location location){
        return blockPosAt(location.getPosition());
    }

    public Location getLocation(BlockPos blockPos, World world){
        return new Location(world.getName(), blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public BlockType getBlock(BlockPos blockPos, World world){
        return world.getBlockType(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    // ---------------------------------------------
    //   ChunkPos
    // ---------------------------------------------

    public static ChunkPos chunkPosAt(WorldChunk chunk){
        return new ChunkPos(chunk.getX(), chunk.getZ());
    }

    public static ChunkPos chunkPosAt(Location location){
        return blockPosAt(location).getChunkPos();
    }

    public static ChunkPos chunkPosAt(Vector3d vector3d){
        return blockPosAt(vector3d).getChunkPos();
    }

    public static ChunkPos chunkPosAt(Vector3i vector3i){
        return blockPosAt(vector3i).getChunkPos();
    }

    public WorldChunk getChunk(ChunkPos chunkPos, World world){
        return world.getChunk(ChunkUtil.indexChunkFromBlock(chunkPos.getX(), chunkPos.getZ()));
    }

    // ---------------------------------------------
    //   LocPos
    // ---------------------------------------------

    public static LocPos locPosAt(Location location){
        Vector3d position = location.getPosition();
        return new LocPos(position.getX(), position.getY(), position.getZ());
    }

    public Location getLocation(LocPos locPos, World world){
        return new Location(world.getName(), locPos.getX(), locPos.getY(), locPos.getZ());
    }

    public BlockType getBlock(LocPos locPos, World world){
        return getBlock(locPos.getBlockPos(), world);
    }

}
