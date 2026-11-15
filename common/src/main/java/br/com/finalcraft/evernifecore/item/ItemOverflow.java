package br.com.finalcraft.evernifecore.item;

/**
 * What happens to the items that did not fit when items are given to a player ({@code FCBukkitUtil.giveItemsTo},
 * {@code FCHytaleUtil.giveItemsTo}): the giver decides, in one value, whether they fall and whether the player is told.
 */
public enum ItemOverflow {

    /** Dropped at the player's feet, and the player is told so. The default. */
    DROP_AND_WARN,

    /** Dropped at the player's feet without a word - the giver tells the player itself, or there is nothing to tell. */
    DROP,

    /** Destroyed: nothing reaches the ground and nobody is told. */
    DISCARD;

    public boolean drops() {
        return this != DISCARD;
    }

    public boolean warns() {
        return this == DROP_AND_WARN;
    }
}
