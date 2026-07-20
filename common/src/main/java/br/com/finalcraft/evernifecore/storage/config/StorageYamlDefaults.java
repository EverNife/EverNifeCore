package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Generates the default {@code storage.yml} programmatically through the EveryConfig
 * {@link Config} (with comment support), instead of bundling a static resource.
 *
 * <p>Each backend entry key is a FREE UNIQUE ID: admins can declare several
 * backends of the same type (e.g. two MySQL servers) by adding entries with
 * different ids. The generated default ships one entry per type - all disabled
 * except {@code groupedfile} (yaml), which is the factory default.</p>
 */
public final class StorageYamlDefaults {

    private StorageYamlDefaults() {
    }

    /** Creates the default storage.yml at the given file (does not overwrite anything if it already exists). */
    public static void writeDefault(File file) {
        if (file.exists()) {
            return;
        }
        Config config = ConfigFactory.open(EverNifeCore.getEcPluginData(), file);

        // ---- storage-backends ----
        // groupedfile (the factory default): one YAML file per player key, holding all their collections
        config.setValue("storage-backends.groupedfile.enabled", true);
        config.setValue("storage-backends.groupedfile.type", "groupedfile");
        config.setValue("storage-backends.groupedfile.path", "plugins/EverNifeCore/StorageData/groupedfile");
        config.setValue("storage-backends.groupedfile.format", "yaml");

        config.setValue("storage-backends.localfile.enabled", false);
        config.setValue("storage-backends.localfile.type", "localfile");
        config.setValue("storage-backends.localfile.path", "plugins/EverNifeCore/StorageData/localfile");
        config.setValue("storage-backends.localfile.format", "yaml");

        config.setValue("storage-backends.h2.enabled", false);
        config.setValue("storage-backends.h2.type", "h2");
        config.setValue("storage-backends.h2.url", "jdbc:h2:file:./plugins/EverNifeCore/StorageData/h2database");

        config.setValue("storage-backends.mysql.enabled", false);
        config.setValue("storage-backends.mysql.type", "sql");
        config.setValue("storage-backends.mysql.url", "jdbc:mysql://localhost:3306/minecraft");
        config.setValue("storage-backends.mysql.user", "root");
        config.setValue("storage-backends.mysql.pass", "");
        config.setValue("storage-backends.mysql.pool.minIdle", 2);
        config.setValue("storage-backends.mysql.pool.maxSize", 10);
        config.setValue("storage-backends.mysql.pool.connectTimeoutSeconds", 5);
        config.setValue("storage-backends.mysql.pool.idleTimeoutSeconds", 30);

        config.setValue("storage-backends.postgresql.enabled", false);
        config.setValue("storage-backends.postgresql.type", "postgresql");
        config.setValue("storage-backends.postgresql.url", "jdbc:postgresql://localhost:5432/minecraft");
        config.setValue("storage-backends.postgresql.user", "root");
        config.setValue("storage-backends.postgresql.pass", "");

        config.setValue("storage-backends.mongo.enabled", false);
        config.setValue("storage-backends.mongo.type", "mongo");
        config.setValue("storage-backends.mongo.url", "mongodb://localhost:27017");
        config.setValue("storage-backends.mongo.db", "minecraft");

        config.setComment("storage-backends", String.join("\n",
                "============================================================",
                " EverNifeCore - Storage (EveryDatabase)",
                "",
                " Storage backends for PlayerData, PDSections and plugin data.",
                " Enable (enabled: true) the ones you want to use; 'groupedfile'",
                " is the default and comes enabled (one YAML file per player).",
                "",
                " Each entry key is a FREE UNIQUE ID - you can declare SEVERAL",
                " backends of the same type pointing to different servers, e.g.:",
                "",
                "   mysql_economy:",
                "     enabled: true",
                "     type: sql",
                "     url: \"jdbc:mysql://economy-db:3306/minecraft\"",
                "   mysql_points:",
                "     enabled: true",
                "     type: sql",
                "     url: \"jdbc:mysql://points-db:3306/minecraft\"",
                "",
                " Valid types: groupedfile | localfile | sql | postgresql | h2 | mongo | memory",
                " Every backend's runtime dependencies (JDBC drivers, Mongo) are",
                " downloaded up front at boot, so switching backend here never",
                " hits a missing dependency.",
                "============================================================"));
        config.setComment("storage-backends.groupedfile.format",
                "format: yaml | json - groupedfile co-locates ALL of a player's collections in one file per key");
        config.setComment("storage-backends.localfile.format",
                "format: yaml | json (json is always pretty/indented)");

        // ---- default-backend ----
        config.setValue("default-backend", "groupedfile");
        config.setComment("default-backend",
                "Backend used when nothing more specific is configured");

        // ---- playerdata ----
        config.setValue("playerdata.storage-backend-id", "groupedfile");
        config.setValue("playerdata.collection", "evernifecore_playerdata");
        config.setValue("playerdata.load-mode", "ALL");
        config.setValue("playerdata.recent-days", 60);
        config.setValue("playerdata.login-timeout-seconds", 5);
        config.setComment("playerdata", String.join("\n",
                "============================================================",
                " Base PlayerData (EverNifeCore)",
                "============================================================"));
        config.setComment("playerdata.storage-backend-id",
                "Must be an enabled storage backend id from 'storage-backends'");
        config.setComment("playerdata.load-mode",
                "ALL = load every player at startup (default) | RECENT = only players seen in the last"
                        + " 'recent-days' (older players lazy-load on demand)");
        config.setComment("playerdata.login-timeout-seconds",
                "How long a login may wait on storage before being denied (storage down)");

        // ---- pdsections ----
        config.setValue("pdsections", new java.util.LinkedHashMap<String, Object>());
        config.setComment("pdsections", String.join("\n",
                "============================================================",
                " PDSections registered by plugins.",
                " Entries are generated automatically on the first registration",
                " of each PDSection - edit the 'storage-backend-id' of each one freely.",
                "============================================================"));

        // ---- multi-server-cache-sync (cross-instance cache coherence) ----
        config.setValue("multi-server-cache-sync.enabled", true);
        config.setValue("multi-server-cache-sync.transport", "auto");
        config.setValue("multi-server-cache-sync.redis.enabled", false);
        config.setValue("multi-server-cache-sync.redis.host", "localhost");
        config.setValue("multi-server-cache-sync.redis.port", 6379);
        config.setComment("multi-server-cache-sync", String.join("\n",
                "============================================================",
                " Multi-server cache-sync (cross-instance cache coherence)",
                "",
                " Only matters when SEVERAL server instances share ONE database.",
                " Keeps each instance's cache fresh: a write on instance A",
                " invalidates the same entry on instance B.",
                "",
                " enabled: true (default) is HARMLESS on a single server - with",
                " no shared signal it is simply a no-op. It starts doing real",
                " work only once there is a shared transport (see below).",
                "============================================================"));
        config.setComment("multi-server-cache-sync.transport", String.join("\n",
                "How the invalidation signal travels between instances:",
                "  auto   - redis if the redis block below is enabled, else the",
                "           backend's native change feed, else off (default)",
                "  redis  - force the redis pub/sub below",
                "  native - use ONLY the backend's native change feed. NOTE: only",
                "           mongo and postgresql have a native feed; sql (MySQL/",
                "           MariaDB) and the file backends do NOT - for those,",
                "           coherence exists only through redis."));
        config.setComment("multi-server-cache-sync.redis", String.join("\n",
                "Single app-level Redis/Valkey block (never one per backend) - a",
                "signalling channel, not a data store. Enable it to carry the sync",
                "signal over ANY backend (required for sql / file backends).",
                "Optional extra keys: user, pass, database, channel, ssl."));

        // ---- multi-platform-accounts (identity layer: a file-level decision, kept near the bottom) ----
        writeMultiplatformAccountsBlock(config);

        // ---- logging ----
        config.setValue("logging.level", "warn");
        config.setComment("logging", String.join("\n",
                "============================================================",
                " Storage logging",
                "============================================================"));
        config.setComment("logging.level", "warn (default) | info | debug | trace");

        config.save();
    }

    /**
     * Appends the {@code multi-platform-accounts} block to an ALREADY EXISTING storage.yml that
     * predates it (a fresh file gets it near the bottom, just above {@code logging}, via
     * {@link #writeDefault(File)}).
     *
     * @return true when the block was written (file saved); false when it already existed
     */
    public static boolean ensureMultiplatformAccounts(Config config) {
        if (config.contains("multi-platform-accounts")) {
            return false;
        }
        writeMultiplatformAccountsBlock(config);
        config.save();
        return true;
    }

    /**
     * Seeds a default inline single-backend block at {@code section} - a plugin's own config field (e.g.
     * config.yml's {@code storage:}) that {@link StorageYamlParser#parseInlineBackend} then reads. The
     * shape mirrors storage.yml's backends but simplified: the child key IS the backend type, enabling is
     * implicit, and EXACTLY ONE is declared. Idempotent: if a backend is already declared it returns
     * untouched, keeping the admin's choice - so it is safe to call on every load.
     *
     * <p>The seeded {@code type}/{@code format} are the plugin's own standardized default, so a plugin can
     * ship whichever default it wants (a groupedfile server, a mongo server, ...). {@code format} is only
     * meaningful for the FILE backends ({@code groupedfile}/{@code localfile}): a {@code null} format on a
     * file backend defaults to {@link BackendDefinition.FileFormat#YAML}, and on any non-file backend the
     * format is dropped altogether (there is no file format to pick - it is overridden to none). Each file
     * backend is rooted in its OWN {@code baseStoragePath/<type>} subfolder, so switching type never makes
     * two backends share a directory (one dir holds one container format).</p>
     *
     * @param section         the field to seed (e.g. {@code config.getConfigSection("storage")})
     * @param baseStoragePath the base data folder; each file backend gets a {@code /<type>} subfolder of it
     *                        (e.g. {@code plugins/MyPlugin/Data} -&gt; {@code plugins/MyPlugin/Data/groupedfile})
     * @param type            the backend type to seed (becomes the single child key); {@code null} = groupedfile
     * @param format          the file format for a file backend ({@code null} = YAML); ignored for non-file types
     * @param compactComment  {@code true} writes the short header; {@code false} the full block documenting
     *                        every switchable type
     */
    public static void writeInlineBackendTemplate(ConfigSection section, String baseStoragePath,
                                                  BackendType type, BackendDefinition.FileFormat format,
                                                  boolean compactComment) {
        if (!section.getKeys().isEmpty()) {
            return; //a backend is already declared - never add a second one or override the admin's choice
        }
        if (type == null) {
            type = BackendType.GROUPEDFILE;
        }

        seedBackendFields(section, type, resolveFileFormat(type, format), baseStoragePath);

        String path = section.getPath();
        if (path != null && !path.isEmpty()) {
            section.getConfig().setDefaultComment(path, compactComment
                    ? inlineBackendTemplateCommentCompact()
                    : inlineBackendTemplateComment(baseStoragePath));
        }
    }

    /**
     * Convenience default: seeds a {@code groupedfile} (YAML) backend documented by the full comment block -
     * the plain "give me the standard file default" call.
     */
    public static void writeInlineBackendTemplate(ConfigSection section, String baseStoragePath) {
        writeInlineBackendTemplate(section, baseStoragePath, BackendType.GROUPEDFILE, null, false);
    }

    /**
     * Seeds a ready {@link BackendDefinition} as the inline single-backend block at {@code section} - its
     * EXACT fields (a file backend's own {@code path}, an sql backend's {@code url}/{@code user}/...), keyed
     * by its own type. This is what {@code ECStorage.open(plugin, section, seedIfAbsent)} writes when the
     * section is empty, so a plugin's factory-built default lands verbatim. Idempotent like the other
     * overloads: a section that already declares a backend is left untouched.
     */
    public static void writeInlineBackendTemplate(ConfigSection section, BackendDefinition definition,
                                                  boolean compactComment) {
        if (!section.getKeys().isEmpty()) {
            return; //a backend is already declared - keep the admin's choice
        }
        seedBackendFieldsFrom(section, definition);

        String path = section.getPath();
        if (path != null && !path.isEmpty()) {
            section.getConfig().setDefaultComment(path, compactComment
                    ? inlineBackendTemplateCommentCompact()
                    : inlineBackendTemplateComment(fileBaseHint(definition)));
        }
    }

    /**
     * Resolves the file format actually written for {@code type}: honoured (defaulting to YAML) for the file
     * backends, and dropped to {@code null} for every other type, which has no file format to pick.
     */
    private static BackendDefinition.FileFormat resolveFileFormat(BackendType type,
                                                                  BackendDefinition.FileFormat requested) {
        if (type != BackendType.GROUPEDFILE && type != BackendType.LOCALFILE) {
            return null;
        }
        return requested != null ? requested : BackendDefinition.FileFormat.YAML;
    }

    /**
     * Writes the per-type default fields of ONE inline backend under {@code section}, keyed by the type's own
     * id. Mirrors the per-type field set that {@link StorageYamlParser#parseInlineBackend} reads back.
     */
    private static void seedBackendFields(ConfigSection section, BackendType type,
                                          BackendDefinition.FileFormat format, String baseStoragePath) {
        String key = type.getId();
        switch (type) {
            case GROUPEDFILE:
            case LOCALFILE:
                section.setValueIfAbsent(key + ".path", baseStoragePath + "/" + key);
                section.setValueIfAbsent(key + ".format", format.name().toLowerCase(Locale.ROOT));
                section.setDefaultComment(key + ".format", type == BackendType.GROUPEDFILE
                        ? "format: yaml | json - groupedfile co-locates a key's whole record in one file"
                        : "format: yaml | json (json is always pretty/indented)");
                break;
            case H2:
                section.setValueIfAbsent(key + ".url", "jdbc:h2:file:./" + baseStoragePath + "/h2database");
                break;
            case SQL:
                section.setValueIfAbsent(key + ".url", "jdbc:mysql://localhost:3306/minecraft");
                section.setValueIfAbsent(key + ".user", "root");
                section.setValueIfAbsent(key + ".pass", "");
                break;
            case POSTGRESQL:
                section.setValueIfAbsent(key + ".url", "jdbc:postgresql://localhost:5432/minecraft");
                section.setValueIfAbsent(key + ".user", "root");
                section.setValueIfAbsent(key + ".pass", "");
                break;
            case MONGO:
                section.setValueIfAbsent(key + ".url", "mongodb://localhost:27017");
                section.setValueIfAbsent(key + ".db", "minecraft");
                break;
            case MEMORY:
                // memory has no fields - seed an empty block so the inline parser still sees a declared backend
                section.setValueIfAbsent(key, new LinkedHashMap<String, Object>());
                break;
        }
    }

    /**
     * Writes the per-type fields of a ready {@link BackendDefinition} under {@code section}, keyed by the
     * definition's own type - its EXACT values (its own path/url/...), unlike {@link #seedBackendFields}
     * which derives file paths from a base folder.
     */
    private static void seedBackendFieldsFrom(ConfigSection section, BackendDefinition definition) {
        BackendType type = definition.getType();
        String key = type.getId();
        switch (type) {
            case GROUPEDFILE:
            case LOCALFILE:
                BackendDefinition.FileFormat format = definition.getFormat() != null
                        ? definition.getFormat() : BackendDefinition.FileFormat.YAML;
                section.setValueIfAbsent(key + ".path", definition.getPath());
                section.setValueIfAbsent(key + ".format", format.name().toLowerCase(Locale.ROOT));
                section.setDefaultComment(key + ".format", type == BackendType.GROUPEDFILE
                        ? "format: yaml | json - groupedfile co-locates a key's whole record in one file"
                        : "format: yaml | json (json is always pretty/indented)");
                break;
            case H2:
                section.setValueIfAbsent(key + ".url", definition.getUrl());
                break;
            case SQL:
            case POSTGRESQL:
                section.setValueIfAbsent(key + ".url", definition.getUrl());
                section.setValueIfAbsent(key + ".user", definition.getUser() != null ? definition.getUser() : "root");
                section.setValueIfAbsent(key + ".pass", definition.getPass() != null ? definition.getPass() : "");
                break;
            case MONGO:
                section.setValueIfAbsent(key + ".url", definition.getUrl());
                section.setValueIfAbsent(key + ".db", definition.getDatabase());
                break;
            case MEMORY:
                section.setValueIfAbsent(key, new LinkedHashMap<String, Object>());
                break;
        }
    }

    /** A base-folder hint for the doc comment's example paths: the parent of a file backend's path, else a placeholder. */
    private static String fileBaseHint(BackendDefinition definition) {
        String path = definition.getPath();
        if (path != null && !path.isEmpty()) {
            int lastSlash = path.replace('\\', '/').lastIndexOf('/');
            return lastSlash > 0 ? path.substring(0, lastSlash) : path;
        }
        return "plugins/YourPlugin/Data";
    }

    /** The full inline-backend header: documents every switchable type and its fields. */
    public static String inlineBackendTemplateComment(String defaultDataPath) {
        return String.join("\n",
                "============================================================",
                " Storage backend (EveryDatabase, via EverNifeCore)",
                "",
                " Declare EXACTLY ONE backend here - the child key IS its type,",
                " To switch backend, replace the block below with another type.",
                "",
                " Valid types and their fields:",
                "",
                "   groupedfile:            # one file per key (the default)",
                "     path: " + defaultDataPath + "/groupedfile",
                "     format: yaml           # yaml | json",
                "",
                "   localfile:              # one file per entity",
                "     path: " + defaultDataPath + "/localfile",
                "     format: yaml",
                "",
                "   h2:",
                "     url: \"jdbc:h2:file:./" + defaultDataPath + "/h2database\"",
                "",
                "   sql:                    # MySQL / MariaDB",
                "     url: \"jdbc:mysql://localhost:3306/minecraft\"",
                "     user: root",
                "     pass: \"\"",
                "",
                "   postgresql:",
                "     url: \"jdbc:postgresql://localhost:5432/minecraft\"",
                "     user: root",
                "     pass: \"\"",
                "",
                "   mongo:",
                "     url: \"mongodb://localhost:27017\"",
                "     db: minecraft",
                "============================================================");
    }

    /** The compact inline-backend header: just the one rule, for configs that document types elsewhere. */
    public static String inlineBackendTemplateCommentCompact() {
        return String.join("\n",
                "============================================================",
                " Storage backend (EveryDatabase, via EverNifeCore)",
                "",
                " Declare EXACTLY ONE backend here - the child key IS its type,",
                " To switch backend, replace the block below with another type.",
                "============================================================");
    }

    private static void writeMultiplatformAccountsBlock(Config config) {
        config.setValue("multi-platform-accounts.enabled", false);
        config.setValue("multi-platform-accounts.storage-backend-id", "");
        config.setComment("multi-platform-accounts", String.join("\n",
                "============================================================",
                " Multi-Platform Accounts",
                "",
                " Links identities from DIFFERENT platforms into ONE account:",
                " Minecraft <-> Hytale <-> external providers (Discord, a",
                " website, ...). Linked players share the account-wide data",
                " (network achievements, VIP status, ...).",
                "",
                " This is a SEPARATE concern from 'multi-server-cache-sync'",
                " above: that one is about running several instances of the",
                " SAME platform on a shared database; this one is about linking",
                " different platforms/identities - meaningful even on a single",
                " server (e.g. linking Discord on a solo server).",
                "",
                " Enabling this writes NOTHING by itself - account rows only",
                " start to exist when identities are actually linked",
                " (/account link). On a real network the backend below must",
                " be a database SHARED by every instance (MariaDB/Mongo/...),",
                " never a local file backend.",
                "============================================================"));
        config.setComment("multi-platform-accounts.storage-backend-id",
                "Backend hosting the WHOLE account family (account registry + account-wide"
                        + " sections). Empty = the 'default-backend'.");
    }
}
