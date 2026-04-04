package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.commons.MinMax;
import br.com.finalcraft.evernifecore.vector.BlockPos;
import org.apache.commons.lang3.Validate;

import java.util.Collection;

public class FCPosUtil {

    public static MinMax<BlockPos> getMinimumAndMaximum(Collection<BlockPos> blockPosList){
        Validate.isTrue(blockPosList.size() > 0, "The list of blockPos must have at least one element!");

        double minX = 0;
        double minY = 0;
        double minZ = 0;
        double maxX = 0;
        double maxY = 0;
        double maxZ = 0;

        boolean firstLoop = true;
        for (BlockPos blockPos : blockPosList) {
            double x = blockPos.getX();
            double y = blockPos.getY();
            double z = blockPos.getZ();

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

}
