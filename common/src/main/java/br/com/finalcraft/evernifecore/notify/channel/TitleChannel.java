package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;

import java.time.Duration;
import java.time.Instant;

/**
 * The big centred title and its subtitle ({@link IHudAccess.Capability#TITLE}): {@link Notification#getTitleOrText()}
 * over {@link Notification#getSubtitle()}, fading in for {@link #FADE_IN_TICKS}, staying for the notification's duration
 * and fading out for {@link #FADE_OUT_TICKS}. A title is sent once when it reaches the head - sending it again would
 * restart its fade - with the times that are left of it.
 */
public final class TitleChannel extends QueuedHudChannel {

    public static final int FADE_IN_TICKS = 10;
    public static final int FADE_OUT_TICKS = 20;
    private static final long MILLIS_PER_TICK = 50L;
    //what "until cleared" stays for: the client takes ticks as an int
    private static final int UNTIL_CLEARED_TICKS = Integer.MAX_VALUE / 2;

    public TitleChannel() {
        super(IHudAccess.Capability.TITLE);
    }

    @Override
    public String getKey() {
        return "evernifecore:title";
    }

    @Override
    protected Duration lifetime(Notification notification) {
        Duration stay = notification.getDuration();
        return stay.isZero() ? Duration.ZERO : stay.plusMillis(MILLIS_PER_TICK * (FADE_IN_TICKS + FADE_OUT_TICKS));
    }

    @Override
    protected void show(IHudAccess hud, FPlayer player, Entry head, boolean changed, Instant now) {
        if (!changed) {
            return;
        }
        Duration duration = head.notification.getDuration();
        long stayTicks = duration.isZero() ? UNTIL_CLEARED_TICKS : duration.toMillis() / MILLIS_PER_TICK;
        long passed = Duration.between(head.start, now).toMillis() / MILLIS_PER_TICK;

        int fadeIn = (int) Math.max(0, FADE_IN_TICKS - passed);
        passed -= FADE_IN_TICKS;
        int stay = (int) Math.max(0, stayTicks - Math.max(0, passed));
        passed -= stayTicks;
        int fadeOut = (int) Math.max(0, FADE_OUT_TICKS - Math.max(0, passed));

        FancyText subtitle = head.notification.getSubtitle();
        hud.sendTitle(player, head.notification.getTitleOrText(), subtitle == null ? FancyText.of("") : subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    protected void blank(IHudAccess hud, FPlayer player) {
        hud.clearTitle(player);
    }
}
