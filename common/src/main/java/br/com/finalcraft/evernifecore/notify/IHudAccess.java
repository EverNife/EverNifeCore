package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.util.ArrayList;
import java.util.List;

/**
 * What a platform can put on a player's screen besides chat: action bar, title, boss bar, sidebar, toast, quest
 * tracker, sound and compass. A platform declares each {@link Capability} it has through {@link #supports}; every
 * call of one it lacks throws {@link UnsupportedOperationException}. Unless a method says otherwise, a call is safe
 * from any thread: the platform writes the packet or hops to the thread it needs by itself.
 */
public interface IHudAccess {

    enum Capability { ACTION_BAR, TITLE, BOSS_BAR, SIDEBAR, TOAST, TRACKER, SOUND, COMPASS }

    /** The access of a platform that can show nothing but chat. */
    IHudAccess UNSUPPORTED = new IHudAccess() {
        @Override
        public boolean supports(Capability capability) {
            return false;
        }
    };

    /** Never throws; the same answer for the life of the process, except where a platform documents a runtime downgrade. */
    boolean supports(Capability capability);

    // ---- ACTION_BAR ----

    /** One text above the hotbar, replaced by the next call; an empty text clears it. */
    default void sendActionBar(FPlayer player, FancyText text) {
        throw unsupported(Capability.ACTION_BAR);
    }

    // ---- TITLE ----

    /** Times in ticks (20 = 1 s); a platform that counts seconds converts. */
    default void sendTitle(FPlayer player, FancyText title, FancyText subtitle, int fadeIn, int stay, int fadeOut) {
        throw unsupported(Capability.TITLE);
    }

    default void clearTitle(FPlayer player) {
        throw unsupported(Capability.TITLE);
    }

    // ---- BOSS_BAR ----

    /** {@code id} is per player; a second call with the same id updates text, progress (0..1) and color. */
    default void showBossBar(FPlayer player, String id, FancyText text, float progress, BossBarColor color) {
        throw unsupported(Capability.BOSS_BAR);
    }

    /** Hides the bar {@code id} of {@code player}; an id never shown is a no-op. */
    default void hideBossBar(FPlayer player, String id) {
        throw unsupported(Capability.BOSS_BAR);
    }

    // ---- SIDEBAR ----

    /**
     * Replaces the whole sidebar; an empty list clears it. The sidebar and the {@link Capability#TRACKER} may share one
     * slot on screen (Minecraft does): the last one shown is drawn, and clearing it draws the other again.
     */
    default void setSidebar(FPlayer player, FancyText title, List<FancyText> lines) {
        throw unsupported(Capability.SIDEBAR);
    }

    default void clearSidebar(FPlayer player) {
        throw unsupported(Capability.SIDEBAR);
    }

    // ---- TOAST ----

    /** A one-shot popup (Minecraft: a throwaway advancement; Hytale: a notification). */
    default void showToast(FPlayer player, FancyText title, FancyText description, ToastStyle style) {
        throw unsupported(Capability.TOAST);
    }

    // ---- TRACKER ----

    /** The structured quest tracker: a title and progress lines; {@code id} is per player, a second call updates it. */
    default void showTracker(FPlayer player, String id, FancyText title, List<TrackerLine> lines) {
        throw unsupported(Capability.TRACKER);
    }

    /** Hides the tracker {@code id} of {@code player}; an id never shown is a no-op. */
    default void hideTracker(FPlayer player, String id) {
        throw unsupported(Capability.TRACKER);
    }

    // ---- SOUND ----

    /**
     * Plays a sound only {@code player} hears. {@code soundId} is a platform sound id, or one of the portable aliases
     * of {@link HudSounds} that each platform maps to a sound of its own era. An id the platform cannot resolve is
     * logged once and plays nothing.
     */
    default void playSound(FPlayer player, String soundId, float volume, float pitch) {
        throw unsupported(Capability.SOUND);
    }

    // ---- COMPASS ----

    /** Points {@code player}'s compass at {@code target}. Thread: the player's world thread (the caller hops). */
    default void setCompassTarget(FPlayer player, WorldLocPos target) {
        throw unsupported(Capability.COMPASS);
    }

    /** Points the compass back at the spawn of the player's world. Thread: the player's world thread. */
    default void resetCompassTarget(FPlayer player) {
        throw unsupported(Capability.COMPASS);
    }

    /** The capabilities this platform lacks, in declaration order. */
    default List<Capability> getUnsupported() {
        List<Capability> missing = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            if (!supports(capability)) {
                missing.add(capability);
            }
        }
        return missing;
    }

    static UnsupportedOperationException unsupported(Capability capability) {
        return new UnsupportedOperationException("This platform does not support " + capability
                + ". Ask IHudAccess.supports(" + capability + ") first, or send through a notify chain that falls back to"
                + " the next channel.");
    }
}
