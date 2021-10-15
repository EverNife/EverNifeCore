package br.com.finalcraft.evernifecore.util;

import com.sk89q.worldedit.BlockVector;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public static Location getLocationFrom(World world, BlockVector blockVector){
        return new Location(world, blockVector.getX(), blockVector.getY(), blockVector.getZ());
    }

    public static BlockVector getBlockVectorFrom(Location location){
        return new BlockVector(location.getX(), location.getY(), location.getZ());
    }

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
        class NearLocation implements Comparable<NearLocation>{
            Location location;
            double distance;
            public NearLocation(Location location, double distance) {
                this.location = location;
                this.distance = distance;
            }

            @Override
            public int compareTo(NearLocation o) {
                return Double.compare(this.distance,o.distance);
            }
        }
        List<NearLocation> orderedNearLocationList = new ArrayList<>();
        for (Location location : locationList) {
            double distance = reference.distance(location);
            if (distance < maxDistance){
                orderedNearLocationList.add(new NearLocation(location,distance));
            }
        }
        Collections.sort(orderedNearLocationList);
        List<Location> newLocationList = new ArrayList<>();
        for (NearLocation nearLocation : orderedNearLocationList) {
            newLocationList.add(nearLocation.location);
        }
        return newLocationList;
    }

    //------------------------------------------------------------------------------------------------------------------

}
