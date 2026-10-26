package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;

/**
 * A waypoint's name, {@code owner:name}: the owner is the plugin that shows it, lowercased, and the name is unique
 * among that plugin's waypoints for one player. Showing a second waypoint with the same id to a player replaces the first.
 */
public final class WaypointId {

    private final String owner;
    private final String name;

    private WaypointId(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    /** @throws IllegalArgumentException when the name is not {@code [a-z0-9_-]{1,64}} */
    public static WaypointId of(ECPluginData owner, String name) {
        return of(owner.getMetaInfo().getName(), name);
    }

    /** @throws IllegalArgumentException when the owner or the name breaks the key rules */
    public static WaypointId of(String owner, String name) {
        return new WaypointId(RegionId.requireOwner(owner), RegionId.requireName("waypoint", name));
    }

    /** Reads {@code owner:name}. */
    public static WaypointId parse(String key) {
        int colon = key.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Invalid waypoint id '" + key + "': write it owner:name, the owner being the"
                    + " plugin that shows it, e.g. 'finalquests:lumber-camp'.");
        }
        return of(key.substring(0, colon), key.substring(colon + 1));
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return owner + ":" + name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WaypointId && ((WaypointId) other).getKey().equals(getKey());
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public String toString() {
        return getKey();
    }
}
