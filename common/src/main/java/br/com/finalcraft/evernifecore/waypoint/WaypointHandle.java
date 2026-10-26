package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One waypoint shown to one player, as {@link Waypoints#show} returned it. */
public final class WaypointHandle {

    final FPlayer player;
    final Waypoint waypoint;
    final List<WaypointChannel> channels;
    final @Nullable Instant until;
    volatile boolean active = true;
    //touched only on the player's world thread: hide undoes only what show drew
    boolean shown;

    WaypointHandle(FPlayer player, Waypoint waypoint, List<WaypointChannel> channels, @Nullable Instant until) {
        this.player = player;
        this.waypoint = waypoint;
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.until = until;
    }

    public FPlayer getPlayer() {
        return player;
    }

    public Waypoint getWaypoint() {
        return waypoint;
    }

    /** The keys of the channels it points through: the supported ones among those asked for. */
    public List<String> getChannelKeys() {
        List<String> keys = new ArrayList<>();
        for (WaypointChannel channel : channels) {
            keys.add(channel.getKey());
        }
        return keys;
    }

    /** False once hidden, expired, replaced, its target gone or its player gone. */
    public boolean isActive() {
        return active;
    }

    /** {@link Waypoints#hide} of this one; a no-op once it is not active. Thread: any. */
    public void hide() {
        Waypoints.get().hide(this);
    }
}
