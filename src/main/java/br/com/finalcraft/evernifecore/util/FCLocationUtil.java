package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.commons.SimpleEntry;
import org.bukkit.Location;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FCLocationUtil {

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
                .filter(location -> location.getWorld().equals(reference.getWorld()))//only locations in the same world
                .map(location -> SimpleEntry.of(location, location.distance(reference)))
                .filter(entry -> entry.getValue() <= maxDistance)
                .sorted(Comparator.comparing(SimpleEntry::getValue))
                .map(SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

}
