package br.com.finalcraft.evernifecore.menu;

/** What {@link Transactions#sellHand} sells. */
public enum HandScope {

    /** The units of the stack in the player's hand. */
    HELD_STACK,
    /** Every unit of the inventory the offer of the held item matches. */
    SAME_ITEM_EVERYWHERE
}
