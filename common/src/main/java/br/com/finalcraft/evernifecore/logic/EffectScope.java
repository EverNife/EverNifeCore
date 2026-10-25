package br.com.finalcraft.evernifecore.logic;

/** How long an effect's change lasts. */
public enum EffectScope {
    /** The change stays until something else changes it. */
    PERMANENT,
    /** The change is held for its holder and given back when the holder lets go; only a holdable effect takes it. */
    HELD
}
