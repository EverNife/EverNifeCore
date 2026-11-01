package br.com.finalcraft.evernifecore.storage;

/** Whether a region, group or quest instance is written to its owner's storage. */
public enum Persistence {
    /** Stored, and back on the next load. */
    PERSISTENT,
    /** Indexed and evented like any other, never stored; gone when its owner disables or its world unloads. */
    TRANSIENT
}
