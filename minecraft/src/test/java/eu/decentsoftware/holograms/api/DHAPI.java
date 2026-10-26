package eu.decentsoftware.holograms.api;

import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A stand-in for DecentHolograms' static API with the signatures of 2.10.2, recording every call. */
public final class DHAPI {

    /** Every call, as {@code method:name}, in order. */
    public static final List<String> CALLS = new CopyOnWriteArrayList<>();
    public static final Map<String, Hologram> HOLOGRAMS = new ConcurrentHashMap<>();

    private DHAPI() {
    }

    public static Hologram createHologram(String name, Location location, boolean saveToFile, List<String> lines) {
        CALLS.add("createHologram:" + name);
        Hologram hologram = new Hologram(name, location, lines);
        HOLOGRAMS.put(name, hologram);
        return hologram;
    }

    public static void moveHologram(String name, Location location) {
        CALLS.add("moveHologram:" + name);
        HOLOGRAMS.get(name).location = location;
    }

    public static void removeHologram(String name) {
        CALLS.add("removeHologram:" + name);
        HOLOGRAMS.remove(name);
    }

    public static void reset() {
        CALLS.clear();
        HOLOGRAMS.clear();
    }
}
