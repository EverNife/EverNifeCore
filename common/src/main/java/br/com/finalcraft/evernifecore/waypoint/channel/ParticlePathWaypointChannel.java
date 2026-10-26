package br.com.finalcraft.evernifecore.waypoint.channel;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import br.com.finalcraft.evernifecore.waypoint.Waypoint;
import br.com.finalcraft.evernifecore.waypoint.WaypointChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code evernifecore:particle-path}: on every refresh, a line of {@code Settings.Waypoint.particle-path-length} points,
 * one block apart, from the player's eyes towards the target, drawn by the platform's outline renderer with
 * {@link VisualStyle#PATH}. A target in another world draws nothing; there is nothing to undo on hide.
 */
public final class ParticlePathWaypointChannel implements WaypointChannel {

    /** How far above the feet the path starts. */
    static final double EYE_HEIGHT = 1.62;

    @Override
    public String getKey() {
        return "evernifecore:particle-path";
    }

    @Override
    public boolean isSupported() {
        return EverNifeCore.getPlatform().getOutlineRenderer() != IOutlineRenderer.NONE;
    }

    @Override
    public boolean isPerPlayer() {
        return true;
    }

    @Override
    public void show(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        refresh(player, waypoint, at);
    }

    @Override
    public void refresh(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        FLocation location = player.getLocation();
        if (location == null) {
            return;
        }
        List<WorldLocPos> points = path(location.getWorldLocPos(), at, ECSettings.WAYPOINT_PARTICLE_PATH_LENGTH);
        if (!points.isEmpty()) {
            EverNifeCore.getPlatform().getOutlineRenderer().render(player, points, VisualStyle.PATH);
        }
    }

    @Override
    public void hide(FPlayer player, Waypoint waypoint) {
        //particles fade by themselves
    }

    /**
     * Up to {@code length} points one block apart from {@code feet}'s eyes towards {@code target}, never past it; the
     * target itself when it is within a block. Empty when the two are in different worlds.
     */
    static List<WorldLocPos> path(WorldLocPos feet, WorldLocPos target, int length) {
        if (!feet.getWorldName().equals(target.getWorldName()) || length < 1) {
            return Collections.emptyList();
        }
        double x = feet.getX(), y = feet.getY() + EYE_HEIGHT, z = feet.getZ();
        double dx = target.getX() - x, dy = target.getY() - y, dz = target.getZ() - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int count = (int) Math.min(length, Math.floor(distance));
        List<WorldLocPos> points = new ArrayList<>();
        if (count == 0) {
            points.add(target);
            return points;
        }
        for (int i = 1; i <= count; i++) {
            double step = i / distance;
            points.add(WorldLocPos.of(x + dx * step, y + dy * step, z + dz * step, target.getWorldName()));
        }
        return points;
    }
}
