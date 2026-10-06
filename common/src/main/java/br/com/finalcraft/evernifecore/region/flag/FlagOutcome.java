package br.com.finalcraft.evernifecore.region.flag;

/** What a flag just did to a player in a region, as its {@link FlagHandler} hears it. */
public enum FlagOutcome {
    /** A protection flag denied the action. */
    DENIED,
    /** The region's value would deny the action, but not for this player - a group or the bypass lifted it. */
    ALLOWED,
    /** A session flag's value now applies to the player, newly or changed. */
    APPLIED,
    /** A session flag's value stopped applying to the player. */
    REMOVED
}
