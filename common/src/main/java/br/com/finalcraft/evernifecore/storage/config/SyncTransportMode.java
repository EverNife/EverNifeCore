package br.com.finalcraft.evernifecore.storage.config;

/**
 * How the multi-server cache-sync invalidation signal travels between instances
 * ({@code multi-server-cache-sync.transport} in storage.yml).
 */
public enum SyncTransportMode {

    /** Redis if the redis block is enabled, else the backend's native change feed, else off. */
    AUTO,

    /** Force the redis pub/sub transport (off when no redis block is enabled). */
    REDIS,

    /**
     * Use only the backend's native change feed. Only mongo and postgresql have one; the sql and
     * file backends have none, so cache-sync is simply off for them under this mode.
     */
    NATIVE
}
