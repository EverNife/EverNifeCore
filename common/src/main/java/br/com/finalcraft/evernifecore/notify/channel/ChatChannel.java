package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;

/** The chat line, {@link Notification#getText()}: every platform has it, so it is the step a chain ends on. */
public final class ChatChannel implements NotifyChannel {

    @Override
    public String getKey() {
        return "evernifecore:chat";
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void deliver(FPlayer player, Notification notification) {
        notification.getText().send(player);
    }
}
