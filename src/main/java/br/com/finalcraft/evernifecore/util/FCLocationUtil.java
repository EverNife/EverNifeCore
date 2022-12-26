package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.commons.SimpleEntry;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FCLocationUtil {

    public static List<Location> getMinimumAndMaximumLocation(List<Location> locationList){
        int minX = locationList.get(0).getBlockX();
        int minY = locationList.get(0).getBlockY();
        int minZ = locationList.get(0).getBlockZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (Location location : locationList) {
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return Arrays.asList(new Location(locationList.get(0).getWorld(),minX, minY, minZ), new Location(locationList.get(0).getWorld(),maxX, maxY, maxZ));
    }

    public static List<Chunk> getAllChunksBetween(Location loc1, Location loc2){
        List<Location> minAndMaxPoints = getMinimumAndMaximumLocation(Arrays.asList(loc1, loc2));
        List<Chunk> allChunks = new ArrayList<>();
        int lowerX = minAndMaxPoints.get(0).getBlockX()>>4;
        int lowerZ = minAndMaxPoints.get(0).getBlockZ()>>4;
        int upperX = minAndMaxPoints.get(1).getBlockX()>>4;
        int upperZ = minAndMaxPoints.get(1).getBlockZ()>>4;
        for (; lowerX <= upperX; lowerX++) {
            for (int z = lowerZ; z <= upperZ; z++) {
                allChunks.add(minAndMaxPoints.get(0).getWorld().getChunkAt(lowerX, z));
            }
        }
        return allChunks;
    }

    //------------------------------------------------------------------------------------------------------------------
    //  Some Location Utilities
    //------------------------------------------------------------------------------------------------------------------

    public static Location getNearestLocation(Location reference, List<Location> locationList){
        return getNearestLocation(reference, locationList, Integer.MAX_VALUE);
    }

    public static Location getNearestLocation(Location reference, List<Location> locationList, int maxDistance){
        List<Location> nearestLocationList = getNearestLocationList(reference,locationList,maxDistance);
        return nearestLocationList.size() > 0 ? nearestLocationList.get(0) : null;
    }

    public static List<Location> getNearestLocationList(Location reference, List<Location> locationList, int maxDistance){
        if (locationList.size() == 0) return null;

        return locationList.stream()
                .map(location -> SimpleEntry.of(location, location.distance(reference)))
                .filter(entry -> entry.getValue() <= maxDistance)
                .sorted(Comparator.comparing(SimpleEntry::getValue))
                .map(SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

}
