package br.com.finalcraft.evernifecore.group;

/** Who an {@link Edge} notice goes to when a player crosses it. */
public enum EdgeAudience {
    /** The player who joined or left. */
    PLAYER,
    /** The other online members. */
    MEMBERS,
    /** The other online members and the player. */
    MEMBERS_AND_PLAYER,
    /** The leader, when online and not the player. */
    LEADER
}
