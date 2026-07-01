package br.com.finalcraft.evernifecore.storage;

import java.util.Locale;

/**
 * Storage backend types supported by storage.yml.
 * Each backend definition's {@code type:} field maps to one of these.
 */
public enum BackendType {

    /** One file per entity, grouped in per-collection sub-directories (pretty JSON or YAML). */
    LOCALFILE("localfile"),

    /**
     * Key-major files: one file per key holding every collection that shares it (YAML or JSON).
     * Best for the per-player model (all of a player's data in one file). The factory default.
     */
    GROUPEDFILE("groupedfile"),

    /** MySQL/MariaDB (HikariCP pool, JSON column). */
    SQL("sql"),

    /** PostgreSQL (HikariCP pool, JSON column). */
    POSTGRESQL("postgresql"),

    /** H2 embedded/file/tcp (HikariCP pool, TEXT column). */
    H2("h2"),

    /** MongoDB (native BSON documents). */
    MONGO("mongo"),

    /** Ephemeral, in-memory only - tests / throwaway servers. */
    MEMORY("memory");

    private final String id;

    BackendType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * @throws StorageConfigException if the id does not match any known type
     */
    public static BackendType fromId(String id) {
        if (id != null) {
            String normalized = id.toLowerCase(Locale.ROOT).trim();
            for (BackendType type : values()) {
                if (type.id.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new StorageConfigException("Unknown storage backend type '" + id + "'!"
                + " Valid types: localfile | groupedfile | sql | postgresql | h2 | mongo | memory");
    }
}
