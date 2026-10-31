package br.com.finalcraft.evernifecore.menu;

/** What a BUY does when the goods do not fit. */
public enum NoSpacePolicy {

    /** Trades anyway: what does not fit becomes a pending delivery the player claims later. */
    DELIVER_LATER,
    /** Refuses with {@link TransactionResult.Failure#NO_SPACE}, charging nothing. */
    REFUSE
}
