package br.com.finalcraft.evernifecore.group;

/** Why a member left a {@link Group}. */
public enum LeaveCause {
    /** The player left by {@link Group#leave}. */
    LEFT,
    /** The member was kicked. */
    KICKED,
    /** A timed membership reached its deadline. */
    EXPIRED,
    /** The player quit the server and the group's quit policy is to leave. */
    QUIT,
    /** The player stopped meeting the condition of a live roster. */
    ROSTER,
    /** The group was dissolved. */
    DISSOLVED,
    /** The owner removed the member by {@link Group#remove}. */
    PLUGIN,
    /** The owner of the group shut down. */
    OWNER_DISABLED
}
