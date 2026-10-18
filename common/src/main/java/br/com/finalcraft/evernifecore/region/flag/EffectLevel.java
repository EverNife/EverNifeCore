package br.com.finalcraft.evernifecore.region.flag;

import java.util.Locale;
import java.util.Objects;

/**
 * A status effect and its level, written {@code effect:level} - {@code speed:2}, {@code minecraft:speed:2}. The effect is
 * the platform's own id, lowercased; a missing level is 1. Which ids exist is the platform's to say.
 */
public final class EffectLevel {

    private final String effect;
    private final int level;

    private EffectLevel(String effect, int level) {
        this.effect = effect;
        this.level = level;
    }

    /** @throws IllegalArgumentException when {@code effect} is blank or {@code level} is under 1 */
    public static EffectLevel of(String effect, int level) {
        String id = effect.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("An effect needs an id, e.g. speed:2.");
        }
        if (level < 1) {
            throw new IllegalArgumentException("The level " + level + " of " + id + " must be 1 or more.");
        }
        return new EffectLevel(id, level);
    }

    /** Reads {@code effect:level}; the level is the part after the last {@code :} when it is a number, else 1. */
    public static EffectLevel parse(String text) {
        String trimmed = text.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1 && trimmed.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            return of(trimmed.substring(0, colon), Integer.parseInt(trimmed.substring(colon + 1)));
        }
        return of(trimmed, 1);
    }

    public String getEffect() {
        return effect;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EffectLevel && level == ((EffectLevel) other).level && effect.equals(((EffectLevel) other).effect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(effect, level);
    }

    @Override
    public String toString() {
        return effect + ":" + level;
    }
}
