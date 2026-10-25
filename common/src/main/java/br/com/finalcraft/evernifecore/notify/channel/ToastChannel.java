package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.notify.ToastStyle;

/**
 * A one-shot popup ({@link IHudAccess.Capability#TOAST}): the title over the text, or the text alone when there is no
 * title. The style follows the kind: {@code COMPLETE} a success, {@code DENY} a danger, the rest info.
 */
public final class ToastChannel implements NotifyChannel {

    @Override
    public String getKey() {
        return "evernifecore:toast";
    }

    @Override
    public boolean isSupported() {
        return EverNifeCore.getPlatform().getHud().supports(IHudAccess.Capability.TOAST);
    }

    @Override
    public void deliver(FPlayer player, Notification notification) {
        boolean titled = notification.getTitle() != null;
        EverNifeCore.getPlatform().getHud().showToast(player, notification.getTitleOrText(),
                titled ? notification.getText() : FancyText.of(""), styleOf(notification.getKind()));
    }

    /** {@code COMPLETE} -> {@code SUCCESS}, {@code DENY} -> {@code DANGER}, every other kind {@code INFO}. */
    public static ToastStyle styleOf(NotifyKind kind) {
        switch (kind) {
            case COMPLETE:
                return ToastStyle.SUCCESS;
            case DENY:
                return ToastStyle.DANGER;
            default:
                return ToastStyle.INFO;
        }
    }
}
