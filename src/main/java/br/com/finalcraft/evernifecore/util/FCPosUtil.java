package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import br.com.finalcraft.evernifecore.util.commons.MinMax;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FCPosUtil {

    public static MinMax<BlockPos> getMinimumAndMaximum(List<BlockPos> blockPos){
        Validate.isTrue(blockPos.size() > 0, "The list of blockPos must have at least one element!");

        int minX = blockPos.get(0).getX();
        int minY = blockPos.get(0).getY();
        int minZ = blockPos.get(0).getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockPos location : blockPos) {
            int x = location.getX();
            int y = location.getY();
            int z = location.getZ();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return MinMax.of(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    public static List<ChunkPos> getAllChunksBetween(BlockPos loc1, BlockPos loc2){
        MinMax<BlockPos> minimumAndMaximum = getMinimumAndMaximum(Arrays.asList(loc1, loc2));

        ChunkPos minChunk = minimumAndMaximum.getMin().getChunkPos();
        ChunkPos maxChunk = minimumAndMaximum.getMax().getChunkPos();

        final int lowerX = minChunk.getX();
        final int lowerZ = minChunk.getZ();
        final int upperX = maxChunk.getX();
        final int upperZ = maxChunk.getZ();

        List<ChunkPos> allChunks = new ArrayList<>();

        for (int x = lowerX; x < upperX; x++) {
            for (int z = lowerZ; z < upperZ; z++) {
                allChunks.add(new ChunkPos(x,z));
            }
        }

        return allChunks;
    }


}
