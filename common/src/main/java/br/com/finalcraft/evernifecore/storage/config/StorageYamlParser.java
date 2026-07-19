package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.log.StorageLogLevel;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parser/validator for {@code plugins/EverNifeCore/storage.yml}.
 *
 * <p>Hard rules (throw {@link StorageConfigException}, fail-fast):</p>
 * <ul>
 *   <li>no backend declared / unknown {@code type} / missing required field</li>
 *   <li>{@code default-backend} missing, not declared or disabled</li>
 *   <li>{@code playerdata.storage-backend-id} not declared or disabled</li>
 *   <li>invalid collection name (must match {@code ^[a-zA-Z][a-zA-Z0-9_]*$})</li>
 * </ul>
 *
 * <p>Soft rules become warnings in the result (the caller logs them):
 * {@code format} on a non-localfile backend, an enabled {@code memory} backend.</p>
 *
 * <p>PDSection entries are loaded as raw data - referencing a missing/disabled
 * backend there is NOT a parse error: it becomes a hard error later, at
 * {@code registerPDSectionCfg} time, with a message naming the section.</p>
 */
public final class StorageYamlParser {

    public static final Pattern VALID_COLLECTION = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    private StorageYamlParser() {
    }

    public static ParsedStorageConfig parse(File storageYmlFile) {
        return parse(ConfigFactory.open(storageYmlFile));
    }

    public static ParsedStorageConfig parse(Config config) {
        List<String> warnings = new ArrayList<>();

        // ---- storage-backends (each key is a free unique backend id) ----
        Map<String, BackendDefinition> backends = new LinkedHashMap<>();
        for (String backendName : config.getKeys("storage-backends")) {
            backends.put(backendName, parseBackend(config, backendName, warnings));
        }
        if (backends.isEmpty()) {
            throw new StorageConfigException("storage.yml has no 'storage-backends:' declared!");
        }

        // ---- default-backend ----
        String defaultBackendName = config.getString("default-backend", null);
        if (defaultBackendName == null || defaultBackendName.isEmpty()) {
            throw new StorageConfigException("storage.yml is missing 'default-backend:'!");
        }
        requireEnabledBackend(backends, defaultBackendName, "'default-backend'");

        // ---- playerdata ----
        PlayerDataAdminConfig playerData = parsePlayerData(config, backends, defaultBackendName);

        // ---- multi-platform-accounts (identity layer): opt-in, one backend for the whole family ----
        boolean multiplatformAccountsEnabled = config.getBoolean("multi-platform-accounts.enabled", false);
        String accountBackendName = emptyToNull(config.getString("multi-platform-accounts.storage-backend-id", null));
        if (accountBackendName != null) {
            requireEnabledBackend(backends, accountBackendName, "'multi-platform-accounts.storage-backend-id'");
        } else {
            accountBackendName = defaultBackendName;
        }

        // ---- pdsections (raw, validated at section registration time) ----
        Map<String, Map<String, PDSectionAdminConfig>> pdSections = new LinkedHashMap<>();
        for (String pluginName : config.getKeys("pdsections")) {
            Map<String, PDSectionAdminConfig> ofPlugin = new LinkedHashMap<>();
            for (String sectionName : config.getKeys("pdsections." + pluginName)) {
                ofPlugin.put(sectionName, parsePDSection(config, pluginName, sectionName));
            }
            pdSections.put(pluginName, ofPlugin);
        }

        // ---- logging ----
        StorageLogLevel loggingLevel = parseLogLevel(config.getString("logging.level", "warn"), warnings);

        // ---- multi-server-cache-sync (multi-instance coherence) ----
        boolean enableSync = config.getBoolean("multi-server-cache-sync.enabled", true);
        SyncTransportMode transportMode = parseTransportMode(
                config.getString("multi-server-cache-sync.transport", "auto"), warnings);
        RedisSyncConfig redisSync = parseRedis(config);   // null unless the redis block is enabled

        return new ParsedStorageConfig(backends, defaultBackendName, multiplatformAccountsEnabled,
                accountBackendName, playerData, pdSections, loggingLevel, enableSync, transportMode,
                redisSync, warnings);
    }

    /** Parses the {@code multi-server-cache-sync.redis} block; {@code null} unless it is enabled. */
    private static RedisSyncConfig parseRedis(Config config) {
        String base = "multi-server-cache-sync.redis";
        if (!config.getBoolean(base + ".enabled", false)) {
            return null;
        }
        String host = config.getString(base + ".host", null);
        if (host == null || host.isEmpty()) {
            throw new StorageConfigException("storage.yml enables 'multi-server-cache-sync.redis'"
                    + " but has no 'host'!");
        }
        int port = config.getInt(base + ".port", RedisSyncConfig.DEFAULT_PORT);
        String username = emptyToNull(config.getString(base + ".user", null));
        String password = emptyToNull(config.getString(base + ".pass", null));
        int database = config.getInt(base + ".database", 0);
        String channel = emptyToNull(config.getString(base + ".channel", null));
        boolean ssl = config.getBoolean(base + ".ssl", false);
        return new RedisSyncConfig(host, port, username, password, database, channel, ssl);
    }

    private static SyncTransportMode parseTransportMode(String raw, List<String> warnings) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "auto":   return SyncTransportMode.AUTO;
            case "redis":  return SyncTransportMode.REDIS;
            case "native": return SyncTransportMode.NATIVE;
            default:
                warnings.add("'multi-server-cache-sync.transport' has unknown value '" + raw
                        + "' - using 'auto'. Valid: auto | redis | native");
                return SyncTransportMode.AUTO;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * Builds a registry with one Storage instance per ENABLED backend.
     * Connects nothing - call {@code registry.initAll()} for that.
     */
    public static StorageRegistry buildRegistry(ParsedStorageConfig parsed, StorageLogConfig logConfig) {
        StorageRegistry registry = new StorageRegistry(parsed.getDefaultBackendName());
        for (BackendDefinition backend : parsed.getBackends().values()) {
            if (backend.isEnabled()) {
                registry.register(backend.getName(), backend.createStorage(logConfig));
            }
        }
        return registry;
    }

    // ---------------------------------------------------------------------

    private static BackendDefinition parseBackend(Config config, String name, List<String> warnings) {
        ConfigSection section = config.getConfigSection("storage-backends." + name);
        BackendType type = BackendType.fromId(section.getString("type", null));
        boolean enabled = section.getBoolean("enabled", false);
        return readBackend(section, name, type, enabled, warnings);
    }

    /**
     * Reads the per-type fields of ONE backend from its own section - the single source of truth shared
     * by the storage.yml form ({@code storage-backends.<id>}, where {@code type}/{@code enabled} are
     * fields) and the inline form ({@link #parseInlineBackend}, where the child key IS the type and
     * enabling is implicit). The paths are relative to {@code section}, so both forms read identically.
     */
    private static BackendDefinition readBackend(ConfigSection section, String name, BackendType type,
                                                 boolean enabled, List<String> warnings) {
        BackendDefinition.FileFormat format = null;
        String formatRaw = section.getString("format", null);
        if (formatRaw != null) {
            if (type != BackendType.LOCALFILE && type != BackendType.GROUPEDFILE) {
                warnings.add("Backend '" + name + "': 'format' is only valid on type localfile/groupedfile"
                        + " - ignored (always JSON).");
            } else if (formatRaw.equalsIgnoreCase("yaml") || formatRaw.equalsIgnoreCase("yml")) {
                format = BackendDefinition.FileFormat.YAML;
            } else if (formatRaw.equalsIgnoreCase("json")) {
                format = BackendDefinition.FileFormat.JSON;
            } else {
                throw new StorageConfigException("Backend '" + name + "': invalid format '"
                        + formatRaw + "' (expected yaml | json)!");
            }
        }

        if (enabled && type == BackendType.MEMORY) {
            warnings.add("Backend '" + name + "' is type 'memory': data is EPHEMERAL and will be"
                    + " lost on shutdown - use only for tests/throwaway servers.");
        }

        Integer poolMinIdle = getIntOrNull(section, "pool.minIdle");
        Integer poolMaxSize = getIntOrNull(section, "pool.maxSize");
        Integer poolConnectTimeout = getIntOrNull(section, "pool.connectTimeoutSeconds");
        Integer poolIdleTimeout = getIntOrNull(section, "pool.idleTimeoutSeconds");

        return BackendDefinition.of(
                name, enabled, type,
                section.getString("url", null),
                section.getString("user", ""),
                section.getString("pass", ""),
                poolMinIdle, poolMaxSize, poolConnectTimeout, poolIdleTimeout,
                section.getString("path", null),
                format,
                section.getString("db", null)
        );
    }

    /**
     * Parses ONE backend declared inline in a plugin's own config (any file, any path), where the
     * SINGLE child key IS the backend type - there is no {@code enabled} (declaring it IS enabling it)
     * and no separate {@code type} field. This is the "declare one and use it directly" shape a plugin
     * routes its own data through, distinct from storage.yml's "declare many, pick one by id".
     *
     * <pre>{@code
     * storage:
     *   mongo:
     *     url: "mongodb://localhost:27017"
     *     db: myplugin
     * }</pre>
     *
     * @param section the parent section whose one child is the backend (e.g. a config.yml's 'storage')
     * @throws StorageConfigException if the section declares no backend, more than one, or an unknown type
     */
    public static BackendDefinition parseInlineBackend(ConfigSection section) {
        return parseInlineBackend(section, new ArrayList<>());
    }

    /** As {@link #parseInlineBackend(ConfigSection)}, collecting soft warnings into {@code warnings}. */
    public static BackendDefinition parseInlineBackend(ConfigSection section, List<String> warnings) {
        Set<String> declared = section.getKeys();
        if (declared.isEmpty()) {
            throw new StorageConfigException("No storage backend declared under '" + sectionLabel(section)
                    + "'! Declare exactly one, keyed by its type"
                    + " (groupedfile | localfile | sql | postgresql | h2 | mongo | memory).");
        }
        if (declared.size() > 1) {
            throw new StorageConfigException("More than one storage backend declared under '"
                    + sectionLabel(section) + "' " + declared + "! Declare EXACTLY ONE - the child key is"
                    + " the backend type; comment the others out.");
        }
        String typeId = declared.iterator().next();
        BackendType type = BackendType.fromId(typeId);
        // implicitly enabled (declaring IS enabling) and named after its own type
        return readBackend(section.getConfigSection(typeId), typeId, type, true, warnings);
    }

    private static String sectionLabel(ConfigSection section) {
        String path = section.getPath();
        return path == null || path.isEmpty() ? "(root)" : path;
    }

    private static PlayerDataAdminConfig parsePlayerData(Config config,
                                                         Map<String, BackendDefinition> backends,
                                                         String defaultBackendName) {
        String backendName = config.getString("playerdata.storage-backend-id", null);
        if (backendName != null) {
            requireEnabledBackend(backends, backendName, "'playerdata.storage-backend-id'");
        }

        String collection = config.getString("playerdata.collection", null);
        if (collection != null) {
            requireValidCollection(collection, "'playerdata.collection'");
        }

        String loadModeRaw = config.getString("playerdata.load-mode", "ALL");
        PlayerDataAdminConfig.LoadMode loadMode;
        try {
            loadMode = PlayerDataAdminConfig.LoadMode.valueOf(loadModeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new StorageConfigException("'playerdata.load-mode' must be ALL or RECENT"
                    + " (found '" + loadModeRaw + "')!");
        }

        int recentDays = config.getInt("playerdata.recent-days", 60);
        if (loadMode == PlayerDataAdminConfig.LoadMode.RECENT && recentDays <= 0) {
            throw new StorageConfigException("'playerdata.recent-days' must be > 0 when"
                    + " load-mode is RECENT!");
        }

        boolean orphanReaperEnabled = config.getBoolean("playerdata.orphan-reaper.enabled", false);
        int orphanReaperInterval = config.getInt("playerdata.orphan-reaper.interval-minutes",
                PlayerDataAdminConfig.DEFAULT_ORPHAN_REAPER_INTERVAL_MINUTES);
        if (orphanReaperEnabled && orphanReaperInterval <= 0) {
            throw new StorageConfigException("'playerdata.orphan-reaper.interval-minutes' must be > 0"
                    + " when the orphan reaper is enabled!");
        }

        int loginTimeoutSeconds = config.getInt("playerdata.login-timeout-seconds",
                PlayerDataAdminConfig.DEFAULT_LOGIN_TIMEOUT_SECONDS);
        if (loginTimeoutSeconds <= 0) {
            throw new StorageConfigException("'playerdata.login-timeout-seconds' must be > 0!");
        }

        return new PlayerDataAdminConfig(backendName, collection, loadMode, recentDays,
                orphanReaperEnabled, orphanReaperInterval, loginTimeoutSeconds);
    }

    private static PDSectionAdminConfig parsePDSection(Config config, String pluginName, String sectionName) {
        String base = "pdsections." + pluginName + "." + sectionName + ".";

        String collection = config.getString(base + "collection", null);
        if (collection != null) {
            requireValidCollection(collection, "'pdsections." + pluginName + "." + sectionName + ".collection'");
        }

        return new PDSectionAdminConfig(
                pluginName,
                sectionName,
                config.getString(base + "storage-backend-id", null),
                collection,
                config.getString(base + "cache.policy", null),
                getIntOrNull(config, base + "cache.ttlSeconds")
        );
    }

    private static StorageLogLevel parseLogLevel(String raw, List<String> warnings) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "warn":  return StorageLogLevel.WARN;
            case "info":  return StorageLogLevel.INFO;
            case "debug": return StorageLogLevel.DEBUG;
            case "trace": return StorageLogLevel.TRACE;
            default:
                warnings.add("'logging.level' has unknown value '" + raw + "' - using 'warn'."
                        + " Valid: warn | info | debug | trace");
                return StorageLogLevel.WARN;
        }
    }

    private static void requireEnabledBackend(Map<String, BackendDefinition> backends,
                                              String backendName, String referencedBy) {
        BackendDefinition backend = backends.get(backendName);
        if (backend == null) {
            throw new StorageConfigException(referencedBy + " points to backend '" + backendName
                    + "', which is not declared under 'storage-backends:' in storage.yml!");
        }
        if (!backend.isEnabled()) {
            throw new StorageConfigException(referencedBy + " points to backend '" + backendName
                    + "', which is DISABLED - set 'storage-backends." + backendName + ".enabled: true'"
                    + " in storage.yml!");
        }
    }

    private static void requireValidCollection(String collection, String referencedBy) {
        if (!VALID_COLLECTION.matcher(collection).matches()) {
            throw new StorageConfigException(referencedBy + " has invalid collection name '"
                    + collection + "' - must match " + VALID_COLLECTION.pattern());
        }
    }

    private static Integer getIntOrNull(Config config, String path) {
        return config.contains(path) ? config.getInt(path) : null;
    }

    private static Integer getIntOrNull(ConfigSection section, String sub) {
        return section.contains(sub) ? section.getInt(sub) : null;
    }
}
