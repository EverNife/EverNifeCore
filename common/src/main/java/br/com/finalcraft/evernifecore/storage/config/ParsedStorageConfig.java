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
    private final String networkBackendName;                        // the one backend the network shares
    private final Integer accountIdleGraceSeconds;                  // nullable -> playerdata default
    private final ServerCooldownsAdminConfig serverCooldowns;       // never null; every key inside is optional
    private final PlayerDataAdminConfig playerData;
    private final Map<String, Map<String, PDSectionAdminConfig>> pdSections; // plugin -> section -> cfg
    private final Map<String, Map<String, PDSectionAdminConfig>> accountSections; // idem, account family
    private final StorageLogLevel loggingLevel;
    private final boolean enableSync;
    private final SyncTransportMode transportMode;
    private final RedisSyncConfig redisSync;                          // nullable: redis block absent or disabled
    private final List<String> warnings;

    ParsedStorageConfig(Map<String, BackendDefinition> backends, String defaultBackendName,
                        String networkBackendName, Integer accountIdleGraceSeconds,
                        ServerCooldownsAdminConfig serverCooldowns, PlayerDataAdminConfig playerData,
                        Map<String, Map<String, PDSectionAdminConfig>> pdSections,
                        Map<String, Map<String, PDSectionAdminConfig>> accountSections,
                        StorageLogLevel loggingLevel, boolean enableSync, SyncTransportMode transportMode,
                        RedisSyncConfig redisSync, List<String> warnings) {
        this.backends = Collections.unmodifiableMap(new LinkedHashMap<>(backends));
        this.defaultBackendName = defaultBackendName;
        this.networkBackendName = networkBackendName;
        this.accountIdleGraceSeconds = accountIdleGraceSeconds;
        this.serverCooldowns = serverCooldowns;
        this.playerData = playerData;
        this.pdSections = Collections.unmodifiableMap(pdSections);
        this.accountSections = Collections.unmodifiableMap(accountSections);
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
     * The one backend every server of the network shares ({@code network.storage-backend-id}): the
     * account registry, every account-wide section and the network-wide server cooldowns. Always an
     * explicitly named, declared and enabled backend - there is no fallback to
     * {@link #getDefaultBackendName()}, since inheriting it silently would move the whole family the
     * day an unrelated key is edited.
     */
    public String getNetworkBackendName() {
        return networkBackendName;
    }

    public PlayerDataAdminConfig getPlayerData() {
        return playerData;
    }

    /**
     * The admin's entry for the network-wide server cooldowns ({@code network.server-cooldowns}).
     * Never null - the block is optional and an absent one simply carries no override.
     */
    public ServerCooldownsAdminConfig getServerCooldowns() {
        return serverCooldowns;
    }

    /**
     * How long an account row stays cached after the last online member of that account quits. Same
     * concept as a PDSection's idle grace and it follows the same server-wide default
     * ({@code playerdata.default-idle-grace-seconds}) unless {@code network.idle-grace-seconds} names
     * its own value - the account family has no per-section config, so this is the one knob for the
     * whole family.
     */
    public int getAccountIdleGraceSeconds() {
        return accountIdleGraceSeconds != null
                ? accountIdleGraceSeconds : playerData.getDefaultIdleGraceSeconds();
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

    /** @see #getPDSection(String, String) - same lookup, over the {@code accountsections} block. */
    public Optional<PDSectionAdminConfig> getAccountSection(String pluginName, String sectionId) {
        if (pluginName == null || sectionId == null) return Optional.empty();
        Map<String, PDSectionAdminConfig> ofPlugin = accountSections.get(pluginName.toLowerCase(Locale.ROOT));
        return ofPlugin == null ? Optional.empty()
                : Optional.ofNullable(ofPlugin.get(sectionId.toLowerCase(Locale.ROOT)));
    }

    public Map<String, Map<String, PDSectionAdminConfig>> getAccountSections() {
        return accountSections;
    }

    /** The entries of one family, so a caller that already knows the family does not branch. */
    public Map<String, Map<String, PDSectionAdminConfig>> getSections(SectionFamily family) {
        return family == SectionFamily.ACCOUNT ? accountSections : pdSections;
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
