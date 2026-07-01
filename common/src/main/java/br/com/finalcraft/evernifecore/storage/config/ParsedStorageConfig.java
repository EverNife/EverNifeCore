package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.everydatabase.log.StorageLogLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final RedisSyncConfig redisSync;                          // nullable: no redis block
    private final List<String> warnings;

    ParsedStorageConfig(Map<String, BackendDefinition> backends, String defaultBackendName,
                        boolean multiplatformAccountsEnabled, String accountBackendName,
                        PlayerDataAdminConfig playerData,
                        Map<String, Map<String, PDSectionAdminConfig>> pdSections,
                        StorageLogLevel loggingLevel, boolean enableSync, RedisSyncConfig redisSync,
                        List<String> warnings) {
        this.backends = Collections.unmodifiableMap(new LinkedHashMap<>(backends));
        this.defaultBackendName = defaultBackendName;
        this.multiplatformAccountsEnabled = multiplatformAccountsEnabled;
        this.accountBackendName = accountBackendName;
        this.playerData = playerData;
        this.pdSections = Collections.unmodifiableMap(pdSections);
        this.loggingLevel = loggingLevel;
        this.enableSync = enableSync;
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
     * Whether the multi-platform account layer is enabled ({@code multiplatform-accounts.enabled}
     * in storage.yml, {@code false} by default). When disabled, every identity resolves to its own
     * singleton account and no account row is ever written.
     */
    public boolean isMultiplatformAccountsEnabled() {
        return multiplatformAccountsEnabled;
    }

    /**
     * The backend hosting the whole account family - the account registry and every account-wide
     * section ({@code multiplatform-accounts.backend} in storage.yml). Shared across instances;
     * falls back to {@link #getDefaultBackendName()} when the admin does not override it. Always an
     * enabled, declared backend.
     */
    public String getAccountBackendName() {
        return accountBackendName;
    }

    public PlayerDataAdminConfig getPlayerData() {
        return playerData;
    }

    public Optional<PDSectionAdminConfig> getPDSection(String pluginName, String sectionName) {
        Map<String, PDSectionAdminConfig> ofPlugin = pdSections.get(pluginName);
        return ofPlugin == null ? Optional.empty() : Optional.ofNullable(ofPlugin.get(sectionName));
    }

    public Map<String, Map<String, PDSectionAdminConfig>> getPDSections() {
        return pdSections;
    }

    public StorageLogLevel getLoggingLevel() {
        return loggingLevel;
    }

    /** Whether cross-instance cache-sync is enabled ({@code enableSync: true} in storage.yml). */
    public boolean isEnableSync() {
        return enableSync;
    }

    /** The single app-level redis block, or {@code null} when none is configured. */
    public RedisSyncConfig getRedisSync() {
        return redisSync;
    }

    /**
     * Whether the admin declared multi-instance intent - the signal the bind-guard uses to reject a
     * versioned entity on a lock-unenforcing backend. True when cache-sync is enabled or a redis
     * block is present.
     */
    public boolean isMultiInstanceIntent() {
        return enableSync || redisSync != null;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
