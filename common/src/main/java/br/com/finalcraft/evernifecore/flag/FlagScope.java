package br.com.finalcraft.evernifecore.flag;

/** Where a flag's value may live: what kind of {@link FlagHolder} may carry it. */
public enum FlagScope {
    /** A place: a region, or a group inside one. The value speaks about what happens THERE. */
    PLACE,
    /** The player: a region, a group in it, or a holder that follows the player wherever they stand (a party). */
    ACTOR
}
