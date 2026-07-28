package br.com.finalcraft.evernifecore.storage.config;

/**
 * A {@code pdsections.<Plugin>.<SectionClass>} override entry from storage.yml.
 * All fields are raw/nullable - the effective values are computed by the
 * BindingResolver's fallback chain.
 *
 * <p>The cache policy lives here as raw data (name + ttl) on purpose: the
 * CachePolicy objects live alongside the playerdata glue and are built at
 * binding-resolution time.</p>
 */
public final class PDSectionAdminConfig {

    private final String pluginName;
    private final String sectionName;
    private final String backendName;     // nullable
    private final String collection;      // nullable
    private final String cachePolicyName; // nullable (ALWAYS | TTL | NOCACHE)
    private final Integer cacheTtlSeconds; // nullable
    private final Integer idleGraceSeconds; // nullable (wins over the developer's advice)

    PDSectionAdminConfig(String pluginName, String sectionName, String backendName,
                         String collection, String cachePolicyName, Integer cacheTtlSeconds,
                         Integer idleGraceSeconds) {
        this.pluginName = pluginName;
        this.sectionName = sectionName;
        this.backendName = backendName;
        this.collection = collection;
        this.cachePolicyName = cachePolicyName;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.idleGraceSeconds = idleGraceSeconds;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getBackendName() {
        return backendName;
    }

    public String getCollection() {
        return collection;
    }

    public String getCachePolicyName() {
        return cachePolicyName;
    }

    public Integer getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    /** Nullable - the admin's per-section idle grace, which beats the developer's advice. */
    public Integer getIdleGraceSeconds() {
        return idleGraceSeconds;
    }
}
