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
        //two file backends out of the box, split by ROLE: what belongs to one player, and what the
        //whole network has to agree on. Both groupedfile (one YAML file per key, holding all that
        //key's collections); the ids name the role because the type is the field right below them
        config.setValue("storage-backends.playerdata.enabled", true);
        config.setValue("storage-backends.playerdata.type", "groupedfile");
        config.setValue("storage-backends.playerdata.path", "plugins/EverNifeCore/StorageData/PlayerData");
        config.setValue("storage-backends.playerdata.format", "yaml");

        config.setValue("storage-backends.networkdata.enabled", true);
        config.setValue("storage-backends.networkdata.type", "groupedfile");
        config.setValue("storage-backends.networkdata.path", "plugins/EverNifeCore/StorageData/NetworkData");
        config.setValue("storage-backends.networkdata.format", "yaml");

        //a disabled EXAMPLE: the folder name is a placeholder on purpose, so enabling it without
        //editing the path reads as unfinished instead of as a deliberate neighbour of the two above
        config.setValue("storage-backends.localfile.enabled", false);
        config.setValue("storage-backends.localfile.type", "localfile");
        config.setValue("storage-backends.localfile.path", "plugins/EverNifeCore/StorageData/SomeRandomFolder");
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
                " Enable (enabled: true) the ones you want to use.",
                "",
                " Two come enabled, split by ROLE rather than by type:",
                "   playerdata  - what belongs to ONE player on THIS server",
                "   networkdata - what the whole network must agree on (accounts,",
                "                 account-wide sections, network cooldowns). Point",
                "                 it at a shared database the day a second server",
                "                 joins; until then a local folder is a network of",
                "                 one, which is correct.",
                " The id names the role and 'type' below names the technology, so",
                " switching networkdata to mysql does not turn its id into a lie.",
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
        config.setComment("storage-backends.playerdata.format",
                "format: yaml | json - groupedfile co-locates ALL of a key's collections in one file per key");
        config.setComment("storage-backends.networkdata.format",
                "format: yaml | json - groupedfile co-locates ALL of a key's collections in one file per key");
        config.setComment("storage-backends.localfile.format",
                "format: yaml | json (json is always pretty/indented)");

        // ---- default-backend ----
        config.setValue("default-backend", "playerdata");
        config.setComment("default-backend",
                "Backend used when nothing more specific is configured");

        // ---- playerdata ----
        config.setValue("playerdata.storage-backend-id", "playerdata");
        config.setValue("playerdata.collection", "evernifecore_playerdata");
        config.setValue("playerdata.load-mode", "ALL");
        config.setValue("playerdata.recent-days", 60);
        config.setValue("playerdata.login-timeout-seconds",
                PlayerDataAdminConfig.DEFAULT_LOGIN_TIMEOUT_SECONDS);
        config.setValue("playerdata.slow-login-report-seconds",
                PlayerDataAdminConfig.DEFAULT_SLOW_LOGIN_REPORT_SECONDS);
        config.setValue("playerdata.default-idle-grace-seconds",
                PlayerDataAdminConfig.DEFAULT_IDLE_GRACE_SECONDS);
        config.setComment("playerdata", String.join("\n",
                "============================================================",
                " Base PlayerData (EverNifeCore)",
                "============================================================"));
        config.setComment("playerdata.storage-backend-id",
                "Must be an enabled storage backend id from 'storage-backends'");
        config.setComment("playerdata.load-mode", String.join("\n",
                "ALL    = load every player at startup (default)",
                "RECENT = only players seen in the last 'recent-days'",
                "         (older players lazy-load on demand)"));
        config.setComment("playerdata.login-timeout-seconds", String.join("\n",
                "How long a login may wait on storage before being denied (storage down).",
                "The login resolves the player row plus every section that loads at login,",
                "so this covers the whole chain, not a single read."));
        config.setComment("playerdata.slow-login-report-seconds", String.join("\n",
                "When a login takes longer than this, the console gets a breakdown of it:",
                "how long each section took, which plugin declared it, and on which backend.",
                "That is what tells you whether a slow login is EverNifeCore, another plugin",
                "or one specific database. 0 turns the report off."));
        config.setComment("playerdata.default-idle-grace-seconds", String.join("\n",
                "How long a player's section stays in memory after that player goes offline.",
                "Applies to every section whose developer did not ask for a specific value.",
                "Raise it so a reconnect does not have to read from the database again;",
                "lower it to reclaim memory sooner - going under 300 (5 min) is not advised,",
                "since a short grace turns every reconnect into a fresh backend read.",
                "One section alone is tuned under 'pdsections.<plugin>.<section>.idle-grace-seconds'."));

        // ---- accountsections ----
        config.setValue("accountsections", new java.util.LinkedHashMap<String, Object>());
        config.setComment("accountsections", String.join("\n",
                "============================================================",
                " AccountSections registered by plugins, keyed by <plugin>.<section-id>.",
                " One row per ACCOUNT, shared by every identity linked to it.",
                " Entries are generated automatically on the first registration.",
                " Per entry you may set:",
                "",
                " accountsections:",
                "   <plugin_id>:",
                "     <section_id>:",
                "       collection: <collection_name>",
                "       cache: { policy: ALWAYS | TTL, ttlSeconds: <n> }",
                "",
                " There is no 'storage-backend-id' here on purpose: the whole account",
                " family lives on the backend set under 'network', which is what lets",
                " a link absorb its rows in one place. A per-section backend would be",
                " a knob nothing could honour - the link would have to coordinate a",
                " write across two databases.",
                " 'cache.policy: TTL' bounds how stale another instance's write may",
                " look on this server when the backend has no change feed.",
                "============================================================"));

        // ---- pdsections ----
        config.setValue("pdsections", new java.util.LinkedHashMap<String, Object>());
        config.setComment("pdsections", String.join("\n",
                "============================================================",
                " PDSections registered by plugins, keyed by <plugin>.<section-id>.",
                " Entries are generated automatically on the first registration of",
                " each PDSection. Per entry you may set:",
                "",
                " pdsections:",
                "   <plugin_id>:",
                "     <section_id>:",
                "       # Controls when the data enters and leaves memory, overriding the",
                "       # plugin. ONLINE (the usual default) loads it during the login,",
                "       # which holds the connection until it is done; LAZY moves it off",
                "       # that path, at the cost of the first read happening whenever",
                "       # something asks. Reach for LAZY when the slow-login report names",
                "       # a heavy section you would rather not pay for at every join.",
                "       lifecycle: LAZY | ONLINE | RESIDENT | PRELOADED",
                "       storage-backend-id: <an enabled backend id>",
                "       collection: <collection_name>",
                "       idle-grace-seconds: <how long a cell lingers after the player leaves>",
                "       cache: { policy: ALWAYS | TTL, ttlSeconds: <n> }   # freshness only",
                "",
                " 'cache' decides freshness; WHEN a section enters and leaves memory",
                " is the developer's lifecycle. NOCACHE is refused: a PDSection is",
                " persisted from its cached instance, so bypassing the cache would",
                " lose every write.",
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
                "  native - use ONLY the backend's native change feed.",
                "",
                "Which backends have a native feed: mongo and postgresql, plus the",
                "file backends. sql (MySQL/MariaDB) has none - there, coherence",
                "exists only through redis.",
                "",
                "A file backend's feed watches THIS machine's filesystem, so it",
                "cannot carry another server's write and buys nothing on a real",
                "network. 'auto' therefore skips it. Set 'native' to turn it on,",
                "which is worth doing where an admin edits the data files by hand",
                "and wants the server to notice."));
        config.setComment("multi-server-cache-sync.redis", String.join("\n",
                "Single app-level Redis/Valkey block (never one per backend) - a",
                "signalling channel, not a data store. Enable it to carry the sync",
                "signal over ANY backend (required for sql / file backends).",
                "Optional extra keys: user, pass, database, channel, ssl."));

        // ---- network (the one backend the whole network shares: a file-level decision, kept near the bottom) ----
        writeNetworkBlock(config);

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
     * Seeds a default inline single-backend block at {@code section} - a plugin's own config field (e.g.
     * config.yml's {@code storage:}) that {@link StorageYamlParser#parseInlineBackend} then reads. The
     * shape mirrors storage.yml's backends but simplified: the child key IS the backend type, enabling is
     * implicit, and EXACTLY ONE is declared. Idempotent: if a backend is already declared it returns
     * untouched, keeping the admin's choice - so it is safe to call on every load.
     *
     * <p>{@code format} is only meaningful for the FILE backends ({@code groupedfile}/{@code localfile}):
     * {@code null} there means {@link BackendDefinition.FileFormat#YAML}, and on a non-file backend it is
     * dropped. Each file backend is rooted in its OWN {@code baseStoragePath/<type>} subfolder, so
     * switching type never makes two backends share a directory.
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
        if (!type.isFileBacked()) {
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

    private static void writeNetworkBlock(Config config) {
        config.setValue("network.storage-backend-id", "networkdata");
        config.setComment("network", String.join("\n",
                "============================================================",
                " Network data",
                "",
                " The one backend every server of your network must agree on.",
                " It holds the account registry, every account-wide section and",
                " the network-wide server cooldowns.",
                "",
                " Two DIFFERENT questions meet here, and only the first one is",
                " configured:",
                "",
                "   'do all my servers see the same row?'",
                "       answered by the backend below. Point every server at",
                "       one shared database (MariaDB/Mongo/...) and they share",
                "       data with NO linking involved, because Minecraft",
                "       servers already agree on a player's uuid.",
                "",
                "   'are these two identities the same person?'",
                "       answered by /ecaccount link, per person, by an admin.",
                "       Needed when the uuid itself differs - a Hytale server,",
                "       a Discord identity, a registration site. Nothing here",
                "       enables or disables it; a link only exists once someone",
                "       runs the command.",
                "",
                " A single server is a network of one: the local backend below",
                " is the right answer until a second server joins.",
                "",
                " network:",
                "   storage-backend-id: <an enabled backend id>",
                "   # How long an account row stays in memory after the LAST online",
                "   # member of that account quits. Absent = follow",
                "   # 'playerdata.default-idle-grace-seconds'.",
                "   idle-grace-seconds: <n>",
                "   server-cooldowns:",
                "     collection: <collection_name>",
                "     cache: { policy: ALWAYS | TTL, ttlSeconds: <n> }",
                "============================================================"));
        config.setComment("network.storage-backend-id",
                "REQUIRED. An enabled backend id from 'storage-backends'. There is no implicit"
                        + " fallback here on purpose: inheriting 'default-backend' silently would move"
                        + " the whole network family the day someone edits an unrelated key.");
    }
}
