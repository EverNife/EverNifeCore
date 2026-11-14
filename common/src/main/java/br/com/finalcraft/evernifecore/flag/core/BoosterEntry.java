package br.com.finalcraft.evernifecore.flag.core;

import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * One entry of {@link BoostersFlag}: what it boosts ({@code on}, the id of a booster domain a plugin declared, e.g.
 * {@code duels:win}) and by how much - a {@code factor} that multiplies the gain, or a {@code bonus} added to the
 * multiplier, exactly one of the two.
 */
public final class BoosterEntry {

    private @Nullable String on;
    private @Nullable Double factor;
    private @Nullable Double bonus;

    public BoosterEntry() {
    }

    public static BoosterEntry factor(String on, double factor) {
        BoosterEntry entry = new BoosterEntry();
        entry.on = on;
        entry.factor = factor;
        return entry;
    }

    public static BoosterEntry bonus(String on, double bonus) {
        BoosterEntry entry = new BoosterEntry();
        entry.on = on;
        entry.bonus = bonus;
        return entry;
    }

    public @Nullable String getOn() {
        return on;
    }

    public void setOn(@Nullable String on) {
        this.on = on;
    }

    public @Nullable Double getFactor() {
        return factor;
    }

    public void setFactor(@Nullable Double factor) {
        this.factor = factor;
    }

    public @Nullable Double getBonus() {
        return bonus;
    }

    public void setBonus(@Nullable Double bonus) {
        this.bonus = bonus;
    }

    /**
     * @throws IllegalArgumentException when {@code on} is missing or not {@code <plugin>:<domain>}, or not exactly one of
     *                                  {@code factor} and {@code bonus} is set
     */
    public void check() {
        if (on == null || on.trim().isEmpty()) {
            throw new IllegalArgumentException("A booster names the domain it boosts: write on=<plugin>:<domain>, e.g. on=duels:win factor=1.25.");
        }
        String id = on.trim();
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("on=" + on + " names no booster domain: write on=<plugin>:<domain>, e.g. on=duels:win."
                    + " A plugin's domains are listed by its export.");
        }
        if ((factor == null) == (bonus == null)) {
            throw new IllegalArgumentException("The booster on " + on + " writes " + (factor == null ? "neither factor nor bonus" : "both factor and bonus")
                    + ": write exactly one - factor=1.25 multiplies the gain, bonus=0.5 adds to its multiplier.");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BoosterEntry)) {
            return false;
        }
        BoosterEntry that = (BoosterEntry) other;
        return Objects.equals(on, that.on) && Objects.equals(factor, that.factor) && Objects.equals(bonus, that.bonus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(on, factor, bonus);
    }

    @Override
    public String toString() {
        return "on=" + on + (factor != null ? " factor=" + factor : "") + (bonus != null ? " bonus=" + bonus : "");
    }
}
