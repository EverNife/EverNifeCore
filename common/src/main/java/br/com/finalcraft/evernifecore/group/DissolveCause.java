package br.com.finalcraft.evernifecore.group;

/** Why a {@link Group} was dissolved. */
public enum DissolveCause {
    /** The owner dissolved it. */
    OWNER,
    /** Its last member left and it dissolves when empty. */
    EMPTY,
    /** It reached the end of its life. */
    EXPIRED,
    /** An effect dissolved it. */
    EFFECT,
    /** The region it was local to was removed, or the region removed it. */
    REGION_REMOVED,
    /** Its owner shut down and it was transient. */
    OWNER_DISABLED
}
