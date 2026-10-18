package br.com.finalcraft.evernifecore.region.playerstate;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a platform can read and change on one player, for the session flags: clock, weather, flight, game mode, effects,
 * health, food and commands. A platform declares each {@link Capability} it has through {@link #supports}; every call of
 * one it lacks throws, and a flag that needs it is reported at boot as having no effect there. Calls run on the
 * player's world thread.
 */
public interface IPlayerStateAccess {

    enum Capability { TIME, WEATHER, FLIGHT, GAME_MODE, EFFECTS, HEALTH, FOOD, COMMAND }

    /** The weather that stops rain and storms, on every platform that has {@link Capability#WEATHER}. */
    String CLEAR = "clear";
    /** The weather that rains, on every platform that has {@link Capability#WEATHER}. */
    String RAIN = "rain";

    /** The access of a platform that can change nothing on a player. */
    IPlayerStateAccess UNSUPPORTED = new IPlayerStateAccess() {
        @Override
        public boolean supports(Capability capability) {
            return false;
        }
    };

    boolean supports(Capability capability);

    // ---- TIME ----

    /** The clock {@code player} sees, or null while it is the world's. */
    default @Nullable PlayerTime getTime(FPlayer player) {
        throw unsupported(Capability.TIME);
    }

    /** Moves {@code player}'s clock, ramping to it over {@code transition} (zero jumps); null gives back the world's clock at once. */
    default void setTime(FPlayer player, @Nullable PlayerTime time, Duration transition) {
        throw unsupported(Capability.TIME);
    }

    // ---- WEATHER ----

    /** The weather {@code player} sees, as {@link #toWeatherId} names it, or null while it is the world's. */
    default @Nullable String getWeather(FPlayer player) {
        throw unsupported(Capability.WEATHER);
    }

    /** Shows {@code player} the weather {@code weather} (an id {@link #toWeatherId} gave); null gives back the world's. */
    default void setWeather(FPlayer player, @Nullable String weather) {
        throw unsupported(Capability.WEATHER);
    }

    /** The id this platform knows {@code text} by - {@link #CLEAR}, {@link #RAIN} or one of its own - or null when it knows none. */
    default @Nullable String toWeatherId(String text) {
        return null;
    }

    // ---- FLIGHT ----

    default boolean getAllowFlight(FPlayer player) {
        throw unsupported(Capability.FLIGHT);
    }

    /** Taking it away also lands a flying player. */
    default void setAllowFlight(FPlayer player, boolean allow) {
        throw unsupported(Capability.FLIGHT);
    }

    default boolean isFlying(FPlayer player) {
        throw unsupported(Capability.FLIGHT);
    }

    default void setFlying(FPlayer player, boolean flying) {
        throw unsupported(Capability.FLIGHT);
    }

    // ---- GAME_MODE ----

    /** Whether this platform has {@code mode} at all - not every server version has {@link ECGameMode#SPECTATOR}. */
    default boolean supportsGameMode(ECGameMode mode) {
        return false;
    }

    default ECGameMode getGameMode(FPlayer player) {
        throw unsupported(Capability.GAME_MODE);
    }

    default void setGameMode(FPlayer player, ECGameMode mode) {
        throw unsupported(Capability.GAME_MODE);
    }

    // ---- EFFECTS ----

    /** Whether {@code effect} - a platform id, lowercased - names an effect here. */
    default boolean isEffect(String effect) {
        return false;
    }

    /** The effect {@code effect} names that {@code player} has now, under that very id, or null. */
    default @Nullable ActiveEffect getEffect(FPlayer player, String effect) {
        throw unsupported(Capability.EFFECTS);
    }

    /** Gives {@code effect} for {@code duration}, replacing one of the same id the player has. */
    default void addEffect(FPlayer player, EffectLevel effect, Duration duration) {
        throw unsupported(Capability.EFFECTS);
    }

    default void removeEffect(FPlayer player, String effect) {
        throw unsupported(Capability.EFFECTS);
    }

    // ---- HEALTH ----

    default double getHealth(FPlayer player) {
        throw unsupported(Capability.HEALTH);
    }

    default double getMaxHealth(FPlayer player) {
        throw unsupported(Capability.HEALTH);
    }

    /** Clamped between zero and {@link #getMaxHealth}. */
    default void setHealth(FPlayer player, double health) {
        throw unsupported(Capability.HEALTH);
    }

    /** Harms {@code player} as the game would - armor, death and all. */
    default void damage(FPlayer player, double amount) {
        throw unsupported(Capability.HEALTH);
    }

    // ---- FOOD ----

    default int getFood(FPlayer player) {
        throw unsupported(Capability.FOOD);
    }

    default int getMaxFood(FPlayer player) {
        throw unsupported(Capability.FOOD);
    }

    /** Clamped between zero and {@link #getMaxFood}. */
    default void setFood(FPlayer player, int food) {
        throw unsupported(Capability.FOOD);
    }

    // ---- COMMAND ----

    /** Runs {@code command} (no leading {@code /}) as {@code asPlayer}, or as the console when it is null. */
    default boolean dispatchCommand(@Nullable FPlayer asPlayer, String command) {
        throw unsupported(Capability.COMMAND);
    }

    /** The capabilities this platform lacks, in declaration order. */
    default List<Capability> getUnsupported() {
        List<Capability> missing = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            if (!supports(capability)) {
                missing.add(capability);
            }
        }
        return missing.isEmpty() ? Collections.emptyList() : missing;
    }

    static UnsupportedOperationException unsupported(Capability capability) {
        return new UnsupportedOperationException("This platform has no " + capability + " capability, so a flag that needs it does nothing"
                + " here - the boot log lists such flags. Ask IPlayerStateAccess.supports(" + capability + ") before calling.");
    }
}
