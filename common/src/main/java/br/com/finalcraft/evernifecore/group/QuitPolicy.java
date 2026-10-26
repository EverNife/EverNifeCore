package br.com.finalcraft.evernifecore.group;

/** What a player's quit does to a membership. */
public enum QuitPolicy {
    /** The member stays, offline. */
    KEEP,
    /** The member leaves with {@link LeaveCause#QUIT}. */
    LEAVE
}
