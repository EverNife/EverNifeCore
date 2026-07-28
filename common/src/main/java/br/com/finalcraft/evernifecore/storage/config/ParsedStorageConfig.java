package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.everydatabase.log.StorageLogLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A fully parsed and validated storage.yml.
 * Immutable snapshot; produced by {@link StorageYamlParser}.
 *
 * <p>Soft issues ('format' ignored on non-localfile backends, an enabled memory
 * backend, ...) are collected in {@link #getWarnings()} - the caller decides how to
 * log them (keeps this layer testable without a live logger).</p>
 */
public final class ParsedStorageConfig {

    private final Map<String, BackendDefinition> backends;          // insertion order
    private final String defaultBackendName;
    private final boolean multiplatformAccountsEnabled;
    private final String accountBackendName;                        // shared account backend (>= default)
    private final PlayerDataAdminConfig playerData;
    private final Map<String, Map<String, PDSectionAdminConfig>> pdSections; // plugin -> section -> cfg
    private final StorageLogLevel loggingLevel;
    private final boolean enableSync;
    private final SyncTransportMode transportMode;
    private final RedisSyncConfig redisSync;                          // nullable: redis block absent or disabled
    private final List<String> warnings;

    ParsedStorageConfig(Map<String, BackendDefinition> backends, String defaultBackendName,
                        boolean multiplatformAccountsEnabled, String accountBackendName,
                        PlayerDataAdminConfig playerData,
                        Map<String, Map<String, PDSectionAdminConfig>> pdSections,
                        StorageLogLevel loggingLevel, boolean enableSync, SyncTransportMode transportMode,
                        RedisSyncConfig redisSync, List<String> warnings) {
        this.backends = Collections.unmodifiableMap(new LinkedHashMap<>(backends));
        this.defaultBackendName = defaultBackendName;
        this.multiplatformAccountsEnabled = multiplatformAccountsEnabled;
        this.accountBackendName = accountBackendName;
        this.playerData = playerData;
        this.pdSections = Collections.unmodifiableMap(pdSections);
        this.loggingLevel = loggingLevel;
        this.enableSync = enableSync;
        this.transportMode = transportMode;
        this.redisSync = redisSync;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    /** All declared backends (enabled or not), in storage.yml order. */
    public Map<String, BackendDefinition> getBackends() {
        return backends;
    }

    public Optional<BackendDefinition> getBackend(String name) {
        return Optional.ofNullable(backends.get(name));
    }

    public String getDefaultBackendName() {
        return defaultBackendName;
    }

    /**
     * Whether the multi-platform account layer is enabled ({@code multi-platform-accounts.enabled}
     * in storage.yml, {@code false} by default). When disabled, every identity resolves to its own
     * singleton account and no account row is ever written.
     */
    public boolean isMultiplatformAccountsEnabled() {
        return multiplatformAccountsEnabled;
    }

    /**
     * The backend hosting the whole account family - the account registry and every account-wide
     * section ({@code multi-platform-accounts.storage-backend-id} in storage.yml). Shared across instances;
     * falls back to {@link #getDefaultBackendName()} when the admin does not override it. Always an
     * enabled, declared backend.
     */
    public String getAccountBackendName() {
        return accountBackendName;
    }

    public PlayerDataAdminConfig getPlayerData() {
        return playerData;
    }

    /**
     * The admin entry of a section, looked up case-insensitively: the generated keys are lowercase,
     * but an admin editing the file by hand should not have to match the case to be obeyed.
     */
    public Optional<PDSectionAdminConfig> getPDSection(String pluginName, String sectionId) {
        if (pluginName == null || sectionId == null) return Optional.empty();
        Map<String, PDSectionAdminConfig> ofPlugin = pdSections.get(pluginName.toLowerCase(Locale.ROOT));
        return ofPlugin == null ? Optional.empty()
                : Optional.ofNullable(ofPlugin.get(sectionId.toLowerCase(Locale.ROOT)));
    }

    public Map<String, Map<String, PDSectionAdminConfig>> getPDSections() {
        return pdSections;
    }

    public StorageLogLevel getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Whether cross-instance cache-sync is enabled ({@code multi-server-cache-sync.enabled}, default
     * {@code true}). On a single server it is a harmless no-op; it only does real work once a shared
     * transport exists (a native change feed or an enabled redis block).
     */
    public boolean isEnableSync() {
        return enableSync;
    }

    /** How the sync signal travels ({@code multi-server-cache-sync.transport}); never null. */
    public SyncTransportMode getTransportMode() {
        return transportMode;
    }

    /** The single app-level redis block, or {@code null} when it is absent or disabled. */
    public RedisSyncConfig getRedisSync() {
        return redisSync;
    }

    /**
     * Whether the admin declared REAL multi-instance intent - the signal the bind-guard uses to warn
     * about a versioned entity on a lock-unenforcing backend. Since cache-sync is on by default, the
     * flag alone no longer signals intent: only an enabled redis block does (the one transport that
     * makes a non-enforcing/feedless backend actually shared across instances).
     */
    public boolean isMultiInstanceIntent() {
        return redisSync != null;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
