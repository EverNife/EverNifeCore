package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.notify.BossBarColor;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyKind;

/**
 * A bar at the top of the screen ({@link IHudAccess.Capability#BOSS_BAR}) with {@link Notification#getText()} and
 * {@link Notification#getProgress()} (full when null); the same id updates it in place. A duration above zero hides it
 * when it runs out; {@code TRACKED} defaults to zero, until cleared. The color follows the kind.
 */
public final class BossBarChannel extends HandleHudChannel {

    public BossBarChannel() {
        super(IHudAccess.Capability.BOSS_BAR);
    }

    @Override
    public String getKey() {
        return "evernifecore:boss-bar";
    }

    @Override
    protected boolean hidesAfterDuration() {
        return true;
    }

    @Override
    protected void show(IHudAccess hud, FPlayer player, String id, Notification notification) {
        Float progress = notification.getProgress();
        hud.showBossBar(player, id, notification.getText(), progress == null ? 1f : progress, colorOf(notification.getKind()));
    }

    @Override
    protected void hide(IHudAccess hud, FPlayer player, String id) {
        hud.hideBossBar(player, id);
    }

    /** {@code COMPLETE} green, {@code DENY} red, {@code TRACKED} purple, the rest blue. */
    public static BossBarColor colorOf(NotifyKind kind) {
        switch (kind) {
            case COMPLETE:
                return BossBarColor.GREEN;
            case DENY:
                return BossBarColor.RED;
            case TRACKED:
                return BossBarColor.PURPLE;
            default:
                return BossBarColor.BLUE;
        }
    }
}
