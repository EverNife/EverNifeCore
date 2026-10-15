package br.com.finalcraft.evernifecore.region.terrain;

/** Where a region's change journal lives. */
public enum JournalPersistence {
    /** Only in memory: a restart or a crash forgets it. The default. */
    MEMORY,
    /** Also in {@code <owner's data folder>/regions/terrain/<region>/journal.json}, written in batches and read back on load. */
    DISK
}
