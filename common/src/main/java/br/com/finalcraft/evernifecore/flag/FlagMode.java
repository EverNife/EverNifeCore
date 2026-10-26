package br.com.finalcraft.evernifecore.flag;

/** How one layer of a flag's value meets the layers ordered after it. */
public enum FlagMode {
    /** Joins the layers after it through the flag's {@link FlagResolver}. */
    ADD,
    /** Replaces the layers after it: the resolution stops at this layer. */
    SET
}
