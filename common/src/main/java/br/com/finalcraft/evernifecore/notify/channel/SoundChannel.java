package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;

/**
 * Plays {@link Notification#getSound()} at full volume and normal pitch ({@link IHudAccess.Capability#SOUND}). A
 * notification without a sound plays nothing, and that is not an error: {@code title+sound} still shows the title.
 */
public final class SoundChannel implements NotifyChannel {

    @Override
    public String getKey() {
        return "evernifecore:sound";
    }

    @Override
    public boolean isSupported() {
        return EverNifeCore.getPlatform().getHud().supports(IHudAccess.Capability.SOUND);
    }

    @Override
    public void deliver(FPlayer player, Notification notification) {
        if (notification.getSound() != null) {
            EverNifeCore.getPlatform().getHud().playSound(player, notification.getSound(), 1f, 1f);
        }
    }
}
