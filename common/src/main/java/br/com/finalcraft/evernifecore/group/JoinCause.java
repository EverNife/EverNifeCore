package br.com.finalcraft.evernifecore.group;

/** How a player came to join a {@link Group}. */
public enum JoinCause {
    /** The owner added them by {@link Group#add}. */
    ADDED,
    /** The player joined by {@link Group#join}. */
    JOINED,
    /** The player accepted an invite. */
    INVITED,
    /** The player started meeting the condition of a live roster. */
    ROSTER
}
