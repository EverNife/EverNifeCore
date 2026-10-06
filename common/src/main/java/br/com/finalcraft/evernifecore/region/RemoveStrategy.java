package br.com.finalcraft.evernifecore.region;

/** What {@link RegionManager#remove(Region, RemoveStrategy)} does with the children of the region it removes. */
public enum RemoveStrategy {
    /** The children stay, with no parent. */
    ORPHAN_CHILDREN,
    /** The children go too, and theirs. */
    CASCADE
}
