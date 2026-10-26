package br.com.finalcraft.evernifecore.waypoint.channel;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.waypoint.Waypoint;
import br.com.finalcraft.evernifecore.waypoint.WaypointChannel;

/** {@code evernifecore:compass}: the player's compass points at the target; hiding points it back at the world spawn. */
public final class CompassWaypointChannel implements WaypointChannel {

    @Override
    public String getKey() {
        return "evernifecore:compass";
    }

    @Override
    public boolean isSupported() {
        return hud().supports(IHudAccess.Capability.COMPASS);
    }

    @Override
    public boolean isPerPlayer() {
        return true;
    }

    @Override
    public void show(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        hud().setCompassTarget(player, at);
    }

    @Override
    public void refresh(FPlayer player, Waypoint waypoint, WorldLocPos at) {
        hud().setCompassTarget(player, at);
    }

    @Override
    public void hide(FPlayer player, Waypoint waypoint) {
        hud().resetCompassTarget(player);
    }

    private static IHudAccess hud() {
        return EverNifeCore.getPlatform().getHud();
    }
}
