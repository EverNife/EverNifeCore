package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import br.com.finalcraft.evernifecore.util.commons.MinMax;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FCPosUtil {

    public static MinMax<BlockPos> getMinimumAndMaximum(Collection<BlockPos> blockPosList){
        Validate.isTrue(blockPosList.size() > 0, "The list of blockPos must have at least one element!");

        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        boolean firstLoop = true;
        for (BlockPos blockPos : blockPosList) {
            int x = blockPos.getX();
            int y = blockPos.getY();
            int z = blockPos.getZ();

            if (firstLoop){
                minX = maxX = x;
                minY = maxY = y;
                minZ = maxZ = z;
                firstLoop = false;
            }

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

        for (int x = lowerX; x <= upperX; x++) {
            for (int z = lowerZ; z <= upperZ; z++) {
                allChunks.add(new ChunkPos(x,z));
            }
        }

        return allChunks;
    }


}
