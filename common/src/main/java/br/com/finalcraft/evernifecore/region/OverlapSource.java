package br.com.finalcraft.evernifecore.region;

/** Where an overlap found by {@link RegionManager#dryRun} comes from. */
public enum OverlapSource {
    /** A region of the same owner as the manager asking. */
    OURS_SAME_OWNER,
    /** A region of another plugin, in this core. */
    OURS_OTHER_OWNER,
    /** A trigger volume another system keeps, reported through an {@code OverlapProvider}. */
    TRIGGER_VOLUMES
}
