package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.TrackerLine;

import java.util.ArrayList;
import java.util.List;

/**
 * The quest tracker ({@link IHudAccess.Capability#TRACKER}): {@link Notification#getTitleOrText()} over
 * {@link Notification#getTrackerLines()}, or over {@link Notification#getLines()} without counts when there are none.
 * The same id updates it in place; it stays until cleared.
 */
public final class TrackerChannel extends HandleHudChannel {

    public TrackerChannel() {
        super(IHudAccess.Capability.TRACKER);
    }

    @Override
    public String getKey() {
        return "evernifecore:tracker";
    }

    @Override
    protected void show(IHudAccess hud, FPlayer player, String id, Notification notification) {
        List<TrackerLine> lines = notification.getTrackerLines();
        if (lines.isEmpty()) {
            lines = new ArrayList<>();
            for (FancyText line : notification.getLines()) {
                lines.add(TrackerLine.of(line));
            }
        }
        hud.showTracker(player, id, notification.getTitleOrText(), lines);
    }

    @Override
    protected void hide(IHudAccess hud, FPlayer player, String id) {
        hud.hideTracker(player, id);
    }
}
