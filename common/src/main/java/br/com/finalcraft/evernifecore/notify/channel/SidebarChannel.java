package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;

/**
 * The scoreboard sidebar ({@link IHudAccess.Capability#SIDEBAR}): {@link Notification#getTitleOrText()} over
 * {@link Notification#getLines()}. A player has one sidebar, so the last notification takes it whatever its id, and
 * clearing that id empties it. Opt-in: no default chain names it.
 */
public final class SidebarChannel extends HandleHudChannel {

    public SidebarChannel() {
        super(IHudAccess.Capability.SIDEBAR);
    }

    @Override
    public String getKey() {
        return "evernifecore:sidebar";
    }

    @Override
    protected boolean singleSlot() {
        return true;
    }

    @Override
    protected void show(IHudAccess hud, FPlayer player, String id, Notification notification) {
        hud.setSidebar(player, notification.getTitleOrText(), notification.getLines());
    }

    @Override
    protected void hide(IHudAccess hud, FPlayer player, String id) {
        hud.clearSidebar(player);
    }
}
