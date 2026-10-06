package br.com.finalcraft.evernifecore.region.flag;

/** Why a local flag value changed. */
public enum FlagChangeCause {
    SET, REMOVED,
    /** A temporary value reached its deadline; a permanent value underneath, if any, is the new value. */
    EXPIRED
}
