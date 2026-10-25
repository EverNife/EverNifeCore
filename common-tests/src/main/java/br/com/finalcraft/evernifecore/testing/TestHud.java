package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.notify.BossBarColor;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import br.com.finalcraft.evernifecore.notify.TrackerLine;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A screen in memory, as {@link Platforms#hud()} answers {@code getHud()}: every capability unless
 * {@link Platforms#hudWithout} took some away, each call kept in order on the list of its capability. A call of a
 * capability this double lacks throws, as a real platform's would - which is how a test becomes "1.7.10" or "Hytale".
 */
public final class TestHud implements IHudAccess {

    /** One call the screen received: for whom, what it did, its main text and whatever else it carried. */
    public static final class HudCall {

        public final FPlayer player;
        /** {@code send}, {@code clear}, {@code show}, {@code hide}, {@code play}, {@code set} or {@code reset}. */
        public final String action;
        /** The main text in its legacy form, or the id for a sound, or null when the call carried none. */
        public final @Nullable String text;
        /** What else the call carried, in parameter order. */
        public final List<Object> extras;

        HudCall(FPlayer player, String action, @Nullable String text, Object... extras) {
            this.player = player;
            this.action = action;
            this.text = text;
            this.extras = Collections.unmodifiableList(Arrays.asList(extras));
        }

        @Override
        public String toString() {
            return (player == null ? "?" : player.getName()) + " " + action + " [" + text + "]" + (extras.isEmpty() ? "" : " " + extras);
        }
    }

    private final Set<Capability> missing = EnumSet.noneOf(Capability.class);
    //channels write from scheduler threads while the test reads, so never a plain ArrayList
    private final List<HudCall> actionBars = new CopyOnWriteArrayList<>();
    private final List<HudCall> titles = new CopyOnWriteArrayList<>();
    private final List<HudCall> bossBars = new CopyOnWriteArrayList<>();
    private final Map<String, HudCall> shownBossBars = new ConcurrentHashMap<>();
    private final List<HudCall> sidebars = new CopyOnWriteArrayList<>();
    private final List<HudCall> toasts = new CopyOnWriteArrayList<>();
    private final List<HudCall> trackers = new CopyOnWriteArrayList<>();
    private final List<HudCall> sounds = new CopyOnWriteArrayList<>();
    private final List<HudCall> compassTargets = new CopyOnWriteArrayList<>();

    /** Takes {@code capabilities} away: {@link #supports} answers false and their calls throw. */
    public TestHud without(Capability... capabilities) {
        missing.addAll(Arrays.asList(capabilities));
        return this;
    }

    @Override
    public boolean supports(Capability capability) {
        return !missing.contains(capability);
    }

    private void require(Capability capability) {
        if (!supports(capability)) {
            throw IHudAccess.unsupported(capability);
        }
    }

    private static String legacy(@Nullable FancyText text) {
        return text == null ? null : text.getText();
    }

    private static String key(FPlayer player, String id) {
        return player.getUniqueId() + "|" + id;
    }

    @Override
    public void sendActionBar(FPlayer player, FancyText text) {
        require(Capability.ACTION_BAR);
        actionBars.add(new HudCall(player, "send", legacy(text)));
    }

    /** Extras: subtitle text, fadeIn, stay, fadeOut. */
    @Override
    public void sendTitle(FPlayer player, FancyText title, FancyText subtitle, int fadeIn, int stay, int fadeOut) {
        require(Capability.TITLE);
        titles.add(new HudCall(player, "send", legacy(title), legacy(subtitle), fadeIn, stay, fadeOut));
    }

    @Override
    public void clearTitle(FPlayer player) {
        require(Capability.TITLE);
        titles.add(new HudCall(player, "clear", null));
    }

    /** Extras: id, progress, color. */
    @Override
    public void showBossBar(FPlayer player, String id, FancyText text, float progress, BossBarColor color) {
        require(Capability.BOSS_BAR);
        HudCall call = new HudCall(player, "show", legacy(text), id, progress, color);
        bossBars.add(call);
        shownBossBars.put(key(player, id), call);
    }

    /** Extras: id. */
    @Override
    public void hideBossBar(FPlayer player, String id) {
        require(Capability.BOSS_BAR);
        bossBars.add(new HudCall(player, "hide", null, id));
        shownBossBars.remove(key(player, id));
    }

    /** Extras: the lines, as legacy strings. */
    @Override
    public void setSidebar(FPlayer player, FancyText title, List<FancyText> lines) {
        require(Capability.SIDEBAR);
        List<String> texts = new ArrayList<>();
        for (FancyText line : lines) {
            texts.add(legacy(line));
        }
        sidebars.add(new HudCall(player, "set", legacy(title), Collections.unmodifiableList(texts)));
    }

    @Override
    public void clearSidebar(FPlayer player) {
        require(Capability.SIDEBAR);
        sidebars.add(new HudCall(player, "clear", null));
    }

    /** Extras: description text, style. */
    @Override
    public void showToast(FPlayer player, FancyText title, FancyText description, ToastStyle style) {
        require(Capability.TOAST);
        toasts.add(new HudCall(player, "show", legacy(title), legacy(description), style));
    }

    /** Extras: id, the lines. */
    @Override
    public void showTracker(FPlayer player, String id, FancyText title, List<TrackerLine> lines) {
        require(Capability.TRACKER);
        trackers.add(new HudCall(player, "show", legacy(title), id, Collections.unmodifiableList(new ArrayList<>(lines))));
    }

    /** Extras: id. */
    @Override
    public void hideTracker(FPlayer player, String id) {
        require(Capability.TRACKER);
        trackers.add(new HudCall(player, "hide", null, id));
    }

    /** {@code text} is the sound id; extras: volume, pitch. */
    @Override
    public void playSound(FPlayer player, String soundId, float volume, float pitch) {
        require(Capability.SOUND);
        sounds.add(new HudCall(player, "play", soundId, volume, pitch));
    }

    /** Extras: the target. */
    @Override
    public void setCompassTarget(FPlayer player, WorldLocPos target) {
        require(Capability.COMPASS);
        compassTargets.add(new HudCall(player, "set", null, target));
    }

    @Override
    public void resetCompassTarget(FPlayer player) {
        require(Capability.COMPASS);
        compassTargets.add(new HudCall(player, "reset", null));
    }

    public List<HudCall> getActionBarCalls() {
        return actionBars;
    }

    public List<HudCall> getTitleCalls() {
        return titles;
    }

    /** Every show and hide, in order. */
    public List<HudCall> getBossBarCalls() {
        return bossBars;
    }

    /** The last {@code show} of the bar {@code id} of {@code player}, or null when it is hidden or was never shown. */
    public @Nullable HudCall getBossBar(FPlayer player, String id) {
        return shownBossBars.get(key(player, id));
    }

    public List<HudCall> getSidebarCalls() {
        return sidebars;
    }

    public List<HudCall> getToastCalls() {
        return toasts;
    }

    public List<HudCall> getTrackerCalls() {
        return trackers;
    }

    public List<HudCall> getSoundCalls() {
        return sounds;
    }

    public List<HudCall> getCompassCalls() {
        return compassTargets;
    }

    /** Forgets every call; the capabilities stay as configured. */
    public void reset() {
        actionBars.clear();
        titles.clear();
        bossBars.clear();
        shownBossBars.clear();
        sidebars.clear();
        toasts.clear();
        trackers.clear();
        sounds.clear();
        compassTargets.clear();
    }
}
