package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;

import java.time.Duration;
import java.time.Instant;

/**
 * The text above the hotbar ({@link IHudAccess.Capability#ACTION_BAR}). The client fades an action bar after a couple
 * of seconds, so the head is written again on every tick for as long as it lasts; an empty text takes it off.
 */
public final class ActionBarChannel extends QueuedHudChannel {

    public ActionBarChannel() {
        super(IHudAccess.Capability.ACTION_BAR);
    }

    @Override
    public String getKey() {
        return "evernifecore:action-bar";
    }

    @Override
    protected Duration lifetime(Notification notification) {
        return notification.getDuration();
    }

    @Override
    protected void show(IHudAccess hud, FPlayer player, Entry head, boolean changed, Instant now) {
        hud.sendActionBar(player, head.notification.getText());
    }

    @Override
    protected void blank(IHudAccess hud, FPlayer player) {
        hud.sendActionBar(player, FancyText.of(""));
    }
}
