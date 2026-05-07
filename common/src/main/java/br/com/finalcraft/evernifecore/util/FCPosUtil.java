package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.math.game.selection.CuboidSelection;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.util.commons.MinMax;
import br.com.finalcraft.evernifecore.util.commons.SimpleEntry;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
        return MinMax.of(BlockPos.of(minX, minY, minZ), BlockPos.of(maxX, maxY, maxZ));
    }

    public static List<ChunkPos> getAllChunksBetween(BlockPos loc1, BlockPos loc2){
        return CuboidSelection.of(loc1, loc2).getChunks();
    }

    public static BlockPos getNearestLocation(BlockPos reference, List<BlockPos> locationList){
        return getNearestLocation(reference, locationList, Integer.MAX_VALUE);
    }

    public static BlockPos getNearestLocation(BlockPos reference, List<BlockPos> locationList, int maxDistance){
        List<BlockPos> nearestLocationList = getNearestLocationList(reference,locationList,maxDistance);
        return nearestLocationList.size() > 0 ? nearestLocationList.get(0) : null;
    }

    public static List<BlockPos> getNearestLocationList(BlockPos reference, List<BlockPos> locationList, int maxDistance){
        if (locationList.size() == 0) return null;

        return locationList.stream()
                .map(location -> SimpleEntry.of(location, location.distance(reference)))
                .filter(entry -> entry.getValue() <= maxDistance)
                .sorted(Comparator.comparing(SimpleEntry::getValue))
                .map(SimpleEntry::getKey)
                .collect(Collectors.toList());
    }
}
