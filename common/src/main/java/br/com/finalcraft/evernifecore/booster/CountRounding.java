package br.com.finalcraft.evernifecore.booster;

/** How {@link BoosterResult#applyToCount} turns a boosted count with a fraction into a whole one. */
public enum CountRounding {
    /** Drops the fraction: 4 x 1.5 = 6, 3 x 1.5 = 4. */
    FLOOR,
    /** The nearest whole, a half going up: 3 x 1.5 = 5. */
    ROUND,
    /** The whole part, plus one more with the fraction as its chance: 3 x 1.5 is 4 or 5, each half the time. */
    CHANCE
}
