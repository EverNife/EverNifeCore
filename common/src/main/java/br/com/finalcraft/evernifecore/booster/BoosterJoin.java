package br.com.finalcraft.evernifecore.booster;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * How the boosters of one gain join into its multiplier, optionally capped. Declared by whoever declares the gain
 * ({@link Boosters#declareJoin}); {@code config.yml} may swap it within what the declaration accepts. Immutable.
 */
public final class BoosterJoin {

    public enum Strategy {
        /** {@code 1 + sum of bonuses}; a factor {@code f} counts as the bonus {@code f - 1}. */
        SUM_BONUS,
        /** The largest multiplier alone. */
        MAX,
        /** The product of every multiplier. */
        PRODUCT
    }

    private final Strategy strategy;
    private final @Nullable BigDecimal cap;

    private BoosterJoin(Strategy strategy, @Nullable BigDecimal cap) {
        this.strategy = strategy;
        this.cap = cap;
    }

    public static BoosterJoin sumBonus() {
        return new BoosterJoin(Strategy.SUM_BONUS, null);
    }

    public static BoosterJoin max() {
        return new BoosterJoin(Strategy.MAX, null);
    }

    public static BoosterJoin product() {
        return new BoosterJoin(Strategy.PRODUCT, null);
    }

    /**
     * Reads {@code sum-bonus}, {@code max} or {@code product}.
     *
     * @throws IllegalArgumentException naming the three
     */
    public static BoosterJoin parse(String strategy) {
        String written = strategy == null ? "" : strategy.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Strategy candidate : Strategy.values()) {
            if (candidate.name().equals(written)) {
                return new BoosterJoin(candidate, null);
            }
        }
        throw new IllegalArgumentException("'" + strategy + "' is not a way to join boosters: write sum-bonus (1 + every bonus),"
                + " max (the largest alone) or product (every factor multiplied).");
    }

    /** This join, never above {@code maxMultiplier}. */
    public BoosterJoin cap(@Nullable BigDecimal maxMultiplier) {
        return new BoosterJoin(strategy, maxMultiplier);
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public @Nullable BigDecimal getCap() {
        return cap;
    }

    /** The multiplier {@code boosters} make; {@code ONE} for none. */
    public BigDecimal join(Collection<Booster> boosters) {
        BigDecimal joined;
        switch (strategy) {
            case MAX:
                joined = BigDecimal.ONE;
                boolean first = true;
                for (Booster booster : boosters) {
                    if (first || booster.getMultiplier().compareTo(joined) > 0) {
                        joined = booster.getMultiplier();
                        first = false;
                    }
                }
                break;
            case PRODUCT:
                joined = BigDecimal.ONE;
                for (Booster booster : boosters) {
                    joined = joined.multiply(booster.getMultiplier());
                }
                break;
            default:
                joined = BigDecimal.ONE;
                for (Booster booster : boosters) {
                    joined = joined.add(booster.getBonusShare());
                }
                joined = joined.max(BigDecimal.ZERO); //bonuses below -1 together never turn a gain into a loss
        }
        return cap != null && joined.compareTo(cap) > 0 ? cap : joined;
    }

    /** {@code sum-bonus}, {@code max} or {@code product}, then {@code cap N} when capped. */
    @Override
    public String toString() {
        return strategy.name().toLowerCase(Locale.ROOT).replace('_', '-') + (cap == null ? "" : " cap " + cap.toPlainString());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BoosterJoin && ((BoosterJoin) other).strategy == strategy && Objects.equals(((BoosterJoin) other).cap, cap);
    }

    @Override
    public int hashCode() {
        return strategy.hashCode() * 31 + Objects.hashCode(cap);
    }
}
