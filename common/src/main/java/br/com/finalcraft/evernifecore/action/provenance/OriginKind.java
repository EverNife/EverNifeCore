package br.com.finalcraft.evernifecore.action.provenance;

/** Where a block, a creature or an item came from, as a {@link ProvenanceTracker} recorded it. */
public enum OriginKind {
    /** A block a player placed. */
    PLAYER_PLACED,
    /** A creature a spawner made. */
    SPAWNER,
    /** A creature hatched from an egg, a spawn egg or a dispensed egg. */
    EGG,
    /** A creature born from two others. */
    BREEDING,
    /** A creature a command or a plugin made. */
    COMMAND,
    /** No record: the world made it, or it predates the recording. Never stored. */
    NATURAL,
    /** An item a player dropped. */
    PLAYER_DROP
}
