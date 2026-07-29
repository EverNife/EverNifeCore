package br.com.finalcraft.evernifecore.storage.config;

/**
 * The admin's entry for the network-wide server cooldowns ({@code network.server-cooldowns} in
 * storage.yml). Two knobs, and the framework can honour both: it builds this descriptor itself and
 * picks this cache policy itself, unlike a third-party collection on the same backend, whose owner
 * decides those.
 *
 * <p>There is no {@code storage-backend-id} here. The rows mount on the network backend for the same
 * reason an account section does: moving the network is one operation over the whole family, and a
 * backend of its own would split exactly the unit that operation works on.</p>
 */
public final class ServerCooldownsAdminConfig {

    private final String collection;
    private final String cachePolicyName;
    private final Integer cacheTtlSeconds;

    ServerCooldownsAdminConfig(String collection, String cachePolicyName, Integer cacheTtlSeconds) {
        this.collection = collection;
        this.cachePolicyName = cachePolicyName;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /** Renamed collection, or {@code null} to keep the framework's own name. */
    public String getCollection() {
        return collection;
    }

    /** {@code ALWAYS} or {@code TTL}; {@code null} keeps the default. NOCACHE is refused at bind. */
    public String getCachePolicyName() {
        return cachePolicyName;
    }

    /** The TTL window in seconds, only meaningful with {@code TTL}; {@code null} otherwise. */
    public Integer getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }
}
