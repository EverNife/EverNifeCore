package br.com.finalcraft.evernifecore.minecraft.waypoint;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.waypoint.Waypoint;
import br.com.finalcraft.evernifecore.waypoint.WaypointChannel;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code evernifecore:web-map}: a marker named by the waypoint's label on the server's web map, through the first of
 * BlueMap, dynmap or squaremap that is enabled, by reflection. A web map is seen by everybody who opens it, so
 * {@link #isPerPlayer()} is false: a plugin that shows a private waypoint leaves this channel out.
 */
public final class McWebMapWaypointChannel implements WaypointChannel {

    static final String BLUEMAP = "BlueMap";
    static final String DYNMAP = "dynmap";
    static final String SQUAREMAP = "squaremap";
    /** The marker set / layer every waypoint marker goes in, one per map. */
    static final String SET_ID = "evernifecore-waypoints";
    static final String SET_LABEL = "Waypoints";

    private final Map<String, Shown> shown = new ConcurrentHashMap<>();

    @Override
    public String getKey() {
        return "evernifecore:web-map";
    }

    @Override
    public boolean isSupported() {
        return backend() != null;
    }

    @Override
    public boolean isPerPlayer() {
        return false;
    }

    @Override
    public void show(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        String backend = backend();
        if (backend == null) {
            return;
        }
        String id = McHologramWaypointChannel.nameOf(player, waypoint);
        remove(id);
        String label = waypoint.getLabel().toPlainText();
        put(backend, id, label, at);
        shown.put(id, new Shown(backend, label, at));
    }

    @Override
    public void refresh(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        String id = McHologramWaypointChannel.nameOf(player, waypoint);
        Shown current = shown.get(id);
        if (current == null) {
            show(player, waypoint, at);
        } else if (!at.equals(current.at)) {
            remove(id);
            put(current.backend, id, current.label, at);
            shown.put(id, new Shown(current.backend, current.label, at));
        }
    }

    @Override
    public void hide(FPlayer player, Waypoint waypoint) {
        remove(McHologramWaypointChannel.nameOf(player, waypoint));
    }

    private @Nullable String backend() {
        for (String plugin : new String[]{BLUEMAP, DYNMAP, SQUAREMAP}) {
            if (EverNifeCore.getPlatform().isPluginEnabled(plugin)) {
                return plugin;
            }
        }
        return null;
    }

    private static void put(String backend, String id, String label, WorldLocPos at) {
        World world = Bukkit.getWorld(at.getWorldName());
        if (world == null) {
            return;
        }
        switch (backend) {
            case BLUEMAP:
                for (Map<String, Object> sets : blueMapSetsOf(world)) {
                    Object set = sets.computeIfAbsent(SET_ID, key -> Reflect.construct(Reflect.type("de.bluecolored.bluemap.api.markers.MarkerSet"), SET_LABEL));
                    Object position = Reflect.construct(Reflect.type("com.flowpowered.math.vector.Vector3d"), at.getX(), at.getY(), at.getZ());
                    Object marker = Reflect.construct(Reflect.type("de.bluecolored.bluemap.api.markers.POIMarker"), label, position);
                    Reflect.call(set, "put", id, marker);
                }
                break;
            case DYNMAP:
                Object set = dynmapSet();
                Object icon = Reflect.call(Reflect.call(dynmap(), "getMarkerAPI"), "getMarkerIcon", "default");
                Reflect.call(set, "createMarker", id, label, world.getName(), at.getX(), at.getY(), at.getZ(), icon, false);
                break;
            default:
                Object layer = squaremapLayer(world);
                if (layer == null) {
                    return;
                }
                Object point = Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.Point"), "of", at.getX(), at.getZ());
                Object circle = Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.marker.Marker"), "circle", point, 3.0);
                Object options = Reflect.call(Reflect.call(Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.marker.MarkerOptions"), "builder"),
                        "hoverTooltip", label), "build");
                Reflect.call(circle, "markerOptions", options);
                Reflect.call(layer, "addMarker", squaremapKey(id), circle);
        }
    }

    private void remove(String id) {
        Shown previous = shown.remove(id);
        if (previous == null) {
            return;
        }
        World world = Bukkit.getWorld(previous.at.getWorldName());
        if (world == null) {
            return;
        }
        switch (previous.backend) {
            case BLUEMAP:
                for (Map<String, Object> sets : blueMapSetsOf(world)) {
                    Object set = sets.get(SET_ID);
                    if (set != null) {
                        Reflect.call(set, "remove", id);
                    }
                }
                break;
            case DYNMAP:
                Object marker = Reflect.call(dynmapSet(), "findMarker", id);
                if (marker != null) {
                    Reflect.call(marker, "deleteMarker");
                }
                break;
            default:
                Object layer = squaremapLayer(world);
                if (layer != null) {
                    Reflect.call(layer, "removeMarker", squaremapKey(id));
                }
        }
    }

    /** The marker-set map of each BlueMap map of {@code world}; none while BlueMap is still loading. */
    @SuppressWarnings("unchecked")
    private static Collection<Map<String, Object>> blueMapSetsOf(World world) {
        List<Map<String, Object>> all = new ArrayList<>();
        Optional<?> api = (Optional<?>) Reflect.callStatic(Reflect.type("de.bluecolored.bluemap.api.BlueMapAPI"), "getInstance");
        if (!api.isPresent()) {
            return all;
        }
        Optional<?> blueWorld = (Optional<?>) Reflect.call(api.get(), "getWorld", world);
        if (blueWorld.isPresent()) {
            for (Object map : (Collection<?>) Reflect.call(blueWorld.get(), "getMaps")) {
                all.add((Map<String, Object>) Reflect.call(map, "getMarkerSets"));
            }
        }
        return all;
    }

    private static Plugin dynmap() {
        return Bukkit.getPluginManager().getPlugin(DYNMAP);
    }

    private static Object dynmapSet() {
        Object markers = Reflect.call(dynmap(), "getMarkerAPI");
        Object set = Reflect.call(markers, "getMarkerSet", SET_ID);
        return set != null ? set : Reflect.call(markers, "createMarkerSet", SET_ID, SET_LABEL, null, false);
    }

    /** The waypoint layer of {@code world}'s squaremap, registered on first use; null when squaremap does not map that world. */
    private static @Nullable Object squaremapLayer(World world) {
        Object api = Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.SquaremapProvider"), "get");
        Object identifier = Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.BukkitAdapter"), "worldIdentifier", world);
        Optional<?> mapWorld = (Optional<?>) Reflect.call(api, "getWorldIfEnabled", identifier);
        if (!mapWorld.isPresent()) {
            return null;
        }
        Object registry = Reflect.call(mapWorld.get(), "layerRegistry");
        Object key = squaremapKey(SET_ID);
        if (!(Boolean) Reflect.call(registry, "hasEntry", key)) {
            Object builder = Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.SimpleLayerProvider"), "builder", SET_LABEL);
            Reflect.call(registry, "register", key, Reflect.call(Reflect.call(builder, "showControls", true), "build"));
        }
        return Reflect.call(registry, "get", key);
    }

    private static Object squaremapKey(String id) {
        return Reflect.callStatic(Reflect.type("xyz.jpenilla.squaremap.api.Key"), "of", id);
    }

    private static final class Shown {
        final String backend;
        final String label;
        final WorldLocPos at;

        Shown(String backend, String label, WorldLocPos at) {
            this.backend = backend;
            this.label = label;
            this.at = at;
        }
    }
}
