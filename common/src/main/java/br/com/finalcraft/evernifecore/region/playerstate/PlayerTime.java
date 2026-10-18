package br.com.finalcraft.evernifecore.region.playerstate;

import java.util.Objects;

/**
 * A clock shown to one player, in Minecraft day ticks (0 = 06:00, 24000 a day): fixed at {@code ticks}, or, when
 * {@code relative}, the world's time moved forward by {@code ticks} and running on.
 */
public final class PlayerTime {

    private final long ticks;
    private final boolean relative;

    public PlayerTime(long ticks, boolean relative) {
        this.ticks = ticks;
        this.relative = relative;
    }

    public long getTicks() {
        return ticks;
    }

    public boolean isRelative() {
        return relative;
    }

    /** {@code ticks|relative}, as a session flag stores it. */
    public String encode() {
        return ticks + "|" + relative;
    }

    public static PlayerTime decode(String text) {
        String[] parts = text.split("\\|", 2);
        return new PlayerTime(Long.parseLong(parts[0]), parts.length > 1 && Boolean.parseBoolean(parts[1]));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerTime && ticks == ((PlayerTime) other).ticks && relative == ((PlayerTime) other).relative;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticks, relative);
    }

    @Override
    public String toString() {
        return encode();
    }
}
