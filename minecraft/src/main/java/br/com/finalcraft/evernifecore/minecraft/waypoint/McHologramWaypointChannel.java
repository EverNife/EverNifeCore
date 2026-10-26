package br.com.finalcraft.evernifecore.minecraft.waypoint;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.waypoint.Waypoint;
import br.com.finalcraft.evernifecore.waypoint.WaypointChannel;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code evernifecore:hologram}: the waypoint's label floating over the target, seen only by its player, through the
 * first of DecentHolograms or FancyHolograms that is enabled. Both are reached by reflection: neither is a dependency of
 * this jar, and without them the channel is simply unsupported.
 */
public final class McHologramWaypointChannel implements WaypointChannel {

    static final String DECENT = "DecentHolograms";
    static final String FANCY = "FancyHolograms";
    /** How far above the target the label floats. */
    static final double LIFT = 2.0;

    private final Map<String, Shown> shown = new ConcurrentHashMap<>();

    @Override
    public String getKey() {
        return "evernifecore:hologram";
    }

    @Override
    public boolean isSupported() {
        return backend() != null;
    }

    @Override
    public boolean isPerPlayer() {
        return true;
    }

    @Override
    public void show(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        String backend = backend();
        if (backend == null || !(player instanceof MinecraftFPlayer)) {
            return;
        }
        Player bukkit = ((MinecraftFPlayer<?>) player).getPlayer();
        Location location = locationOf(bukkit, at);
        if (location == null) {
            return;
        }
        String name = nameOf(player, waypoint);
        remove(name);
        List<String> lines = Arrays.asList(waypoint.getLabel().toLegacyString().split("\n"));
        Object hologram = DECENT.equals(backend) ? createDecent(name, location, lines, bukkit) : createFancy(name, location, lines, bukkit);
        shown.put(name, new Shown(backend, hologram, bukkit, at));
    }

    @Override
    public void refresh(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        String name = nameOf(player, waypoint);
        Shown current = shown.get(name);
        if (current == null) {
            show(player, waypoint, at);
            return;
        }
        Location location = locationOf(current.viewer, at);
        if (location == null || at.equals(current.at)) {
            return;
        }
        current.at = at;
        if (DECENT.equals(current.backend)) {
            Reflect.callStatic(Reflect.type("eu.decentsoftware.holograms.api.DHAPI"), "moveHologram", name, location);
        } else {
            Reflect.call(Reflect.call(current.hologram, "getData"), "setLocation", location);
            Reflect.call(current.hologram, "queueUpdate");
        }
    }

    @Override
    public void hide(FPlayer player, Waypoint waypoint) {
        remove(nameOf(player, waypoint));
    }

    private @Nullable String backend() {
        if (EverNifeCore.getPlatform().isPluginEnabled(DECENT)) {
            return DECENT;
        }
        return EverNifeCore.getPlatform().isPluginEnabled(FANCY) ? FANCY : null;
    }

    private static Object createDecent(String name, Location location, List<String> lines, Player viewer) {
        Object hologram = Reflect.callStatic(Reflect.type("eu.decentsoftware.holograms.api.DHAPI"), "createHologram", name, location, false, lines);
        //hidden by default, shown to the one viewer; re-applied to whoever already received it at creation
        Reflect.call(hologram, "setDefaultVisibleState", false);
        Reflect.call(hologram, "setShowPlayer", viewer);
        Reflect.call(hologram, "hideAll");
        Reflect.call(hologram, "showAll");
        return hologram;
    }

    private static Object createFancy(String name, Location location, List<String> lines, Player viewer) {
        Object data = Reflect.construct(Reflect.type("de.oliver.fancyholograms.api.data.TextHologramData"), name, location);
        Reflect.call(data, "setText", lines);
        Class<?> visibility = Reflect.type("de.oliver.fancyholograms.api.data.property.Visibility");
        Reflect.call(data, "setVisibility", Reflect.callStatic(visibility, "valueOf", "MANUAL"));
        Reflect.call(data, "setPersistent", false);
        Object manager = Reflect.call(Reflect.callStatic(Reflect.type("de.oliver.fancyholograms.api.FancyHologramsPlugin"), "get"), "getHologramManager");
        Object hologram = Reflect.call(manager, "create", data);
        Reflect.call(manager, "addHologram", hologram);
        Reflect.callStatic(Reflect.type("de.oliver.fancyholograms.api.data.property.Visibility$ManualVisibility"),
                "addDistantViewer", hologram, viewer.getUniqueId());
        Reflect.call(hologram, "forceUpdateShownStateFor", viewer);
        return hologram;
    }

    private void remove(String name) {
        Shown previous = shown.remove(name);
        if (previous == null) {
            return;
        }
        if (DECENT.equals(previous.backend)) {
            Reflect.callStatic(Reflect.type("eu.decentsoftware.holograms.api.DHAPI"), "removeHologram", name);
        } else {
            Reflect.callStatic(Reflect.type("de.oliver.fancyholograms.api.data.property.Visibility$ManualVisibility"),
                    "removeDistantViewer", previous.hologram, previous.viewer.getUniqueId());
            Object manager = Reflect.call(Reflect.callStatic(Reflect.type("de.oliver.fancyholograms.api.FancyHologramsPlugin"), "get"), "getHologramManager");
            Reflect.call(manager, "removeHologram", previous.hologram);
        }
    }

    /** One hologram per player and waypoint; both plugins take only {@code [A-Za-z0-9_-]} in a name. */
    static String nameOf(FPlayer player, Waypoint waypoint) {
        return ("enc-wp-" + player.getUniqueId() + "-" + waypoint.getId().getOwner() + "-" + waypoint.getId().getName())
                .replaceAll("[^A-Za-z0-9_-]", "_");
    }

    //the viewer's own world first: the usual case, and the one that needs no server lookup
    static @Nullable Location locationOf(Player viewer, WorldLocPos at) {
        World world = viewer.getWorld().getName().equals(at.getWorldName()) ? viewer.getWorld() : Bukkit.getWorld(at.getWorldName());
        return world == null ? null : new Location(world, at.getX(), at.getY() + LIFT, at.getZ());
    }

    private static final class Shown {
        final String backend;
        final Object hologram;
        final Player viewer;
        WorldLocPos at;

        Shown(String backend, Object hologram, Player viewer, WorldLocPos at) {
            this.backend = backend;
            this.hologram = hologram;
            this.viewer = viewer;
            this.at = at;
        }
    }
}
