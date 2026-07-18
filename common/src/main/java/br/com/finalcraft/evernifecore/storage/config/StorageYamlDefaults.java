package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;

import java.io.File;

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
        config.setValue("storage-backends.mysql.url", "jdbc:mariadb://localhost:3306/minecraft");
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
                "     url: \"jdbc:mariadb://economy-db:3306/minecraft\"",
                "   mysql_points:",
                "     enabled: true",
                "     type: sql",
                "     url: \"jdbc:mariadb://points-db:3306/minecraft\"",
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
     * implicit, and EXACTLY ONE is declared. Idempotent (seeds only what is absent), so it is safe to call
     * on every load. Ships a {@code groupedfile} default under {@code defaultDataPath/groupedfile} plus a
     * comment documenting the other types a plugin's admin can switch to. Each FILE backend (groupedfile,
     * localfile) is rooted in its OWN {@code defaultDataPath/<type>} subfolder, so switching type never
     * makes two backends share a directory (they must not - one dir holds one container format).
     *
     * @param section         the field to seed (e.g. {@code config.getConfigSection("storage")})
     * @param defaultDataPath the base data folder; each file backend gets a {@code /<type>} subfolder of it
     *                        (e.g. {@code plugins/MyPlugin/Data} -&gt; {@code plugins/MyPlugin/Data/groupedfile})
     */
    public static void writeInlineBackendTemplate(ConfigSection section, String defaultDataPath) {
        if (!section.getKeys().isEmpty()) {
            return; //a backend is already declared - never add a second one or override the admin's choice
        }
        section.setValueIfAbsent("groupedfile.path", defaultDataPath + "/groupedfile");
        section.setValueIfAbsent("groupedfile.format", "yaml");
        section.setDefaultComment("groupedfile.format",
                "format: yaml | json - groupedfile co-locates a key's whole record in one file");
        String path = section.getPath();
        if (path != null && !path.isEmpty()) {
            section.getConfig().setDefaultComment(path, inlineBackendTemplateComment(defaultDataPath));
        }
    }

    private static String inlineBackendTemplateComment(String defaultDataPath) {
        return String.join("\n",
                "============================================================",
                " Storage backend (EveryDatabase, via EverNifeCore)",
                "",
                " Declare EXACTLY ONE backend here - the child key IS its type,",
                " and declaring it is what enables it (no 'enabled', no 'type').",
                " To switch backend, replace the block below with another type.",
                "",
                " Valid types and their fields:",
                "   groupedfile:            # one file per key (the default)",
                "     path: " + defaultDataPath + "/groupedfile",
                "     format: yaml           # yaml | json",
                "   localfile:              # one file per entity",
                "     path: " + defaultDataPath + "/localfile",
                "     format: yaml",
                "   h2:",
                "     url: \"jdbc:h2:file:./" + defaultDataPath + "/h2database\"",
                "   sql:                    # MySQL / MariaDB",
                "     url: \"jdbc:mariadb://localhost:3306/minecraft\"",
                "     user: root",
                "     pass: \"\"",
                "   postgresql:",
                "     url: \"jdbc:postgresql://localhost:5432/minecraft\"",
                "     user: root",
                "     pass: \"\"",
                "   mongo:",
                "     url: \"mongodb://localhost:27017\"",
                "     db: minecraft",
                "",
                " Every backend's driver is downloaded by EverNifeCore at boot,",
                " so any type here works with no extra install.",
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
