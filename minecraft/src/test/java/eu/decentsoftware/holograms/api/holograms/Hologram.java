package eu.decentsoftware.holograms.api.holograms;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A stand-in for DecentHolograms' hologram, keeping the visibility state the real one keeps. */
public class Hologram {

    public final String name;
    public final List<String> lines;
    public Location location;
    public boolean defaultVisibleState = true;
    public final Set<UUID> showPlayers = ConcurrentHashMap.newKeySet();
    public int showAllCalls;

    public Hologram(String name, Location location, List<String> lines) {
        this.name = name;
        this.location = location;
        this.lines = new ArrayList<>(lines);
    }

    public void setDefaultVisibleState(boolean state) {
        this.defaultVisibleState = state;
    }

    public void setShowPlayer(Player player) {
        showPlayers.add(player.getUniqueId());
    }

    public void hideAll() {
    }

    public void showAll() {
        showAllCalls++;
    }
}
