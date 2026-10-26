package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

/**
 * One way of pointing a player at a waypoint: the compass, a particle path, a hologram, a web-map marker. A plugin adds
 * its own through {@link Waypoints#register}; a waypoint names it by its bare name or its {@code owner:name} key.
 * {@link #show}, {@link #refresh} and {@link #hide} run on the player's world thread.
 */
public interface WaypointChannel {

    /** {@code owner:name}, e.g. {@code evernifecore:compass}; the name part is what a waypoint writes. */
    String getKey();

    /** Whether this platform can point through this channel now (a HUD capability, a renderer, a plugin enabled). Never throws. */
    boolean isSupported();

    /** False when everybody sees what it draws (a web-map marker): the plugin decides whether that is wanted. */
    boolean isPerPlayer();

    /** Starts pointing {@code player} at {@code at}. */
    void show(FPlayer player, Waypoint waypoint, WorldLocPos at);

    /** The target was read again ({@code Settings.Waypoint.refresh-ticks}); {@code at} may have moved. */
    void refresh(FPlayer player, Waypoint waypoint, WorldLocPos at);

    /** Stops pointing: hidden, expired, the target is gone, or the player quit. */
    void hide(FPlayer player, Waypoint waypoint);
}
