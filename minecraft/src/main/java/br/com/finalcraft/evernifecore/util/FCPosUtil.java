package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import br.com.finalcraft.evernifecore.util.commons.MinMax;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import org.apache.commons.lang3.Validate;

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
        return CuboidSelection.of(loc1, loc2).getChunks();
    }


}
