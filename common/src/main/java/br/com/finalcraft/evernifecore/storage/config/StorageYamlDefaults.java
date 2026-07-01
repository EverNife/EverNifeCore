package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;

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
        Config config = ConfigFactory.open(file);

        // ---- multiplatform-accounts (first entry: the identity layer is a file-level decision) ----
        writeMultiplatformAccountsBlock(config);

        // ---- storage-backends ----
        // groupedfile (the factory default): one YAML file per player key, holding all their collections
        config.setValue("storage-backends.groupedfile.enabled", true);
        config.setValue("storage-backends.groupedfile.type", "groupedfile");
        config.setValue("storage-backends.groupedfile.path", "plugins/EverNifeCore/StorageData");
        config.setValue("storage-backends.groupedfile.format", "yaml");

        config.setValue("storage-backends.localfile.enabled", false);
        config.setValue("storage-backends.localfile.type", "localfile");
        config.setValue("storage-backends.localfile.path", "plugins/EverNifeCore/StorageData");
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
                " The dependencies of each backend (JDBC drivers, Mongo) are only",
                " downloaded when the backend is enabled.",
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
        config.setValue("playerdata.backend", "groupedfile");
        config.setValue("playerdata.collection", "evernifecore_playerdata");
        config.setValue("playerdata.load-mode", "ALL");
        config.setValue("playerdata.recent-days", 60);
        config.setValue("playerdata.login-timeout-seconds", 5);
        config.setComment("playerdata", String.join("\n",
                "============================================================",
                " Base PlayerData (EverNifeCore)",
                "============================================================"));
        config.setComment("playerdata.backend", "Must be an enabled backend of 'storage-backends'");
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
                " of each PDSection - edit the 'backend' of each one freely.",
                "============================================================"));

        // ---- cache-sync (cross-instance coherence) ----
        config.setValue("enableSync", false);
        config.setComment("enableSync", String.join("\n",
                "============================================================",
                " Cross-instance cache coherence (multi-server networks)",
                "",
                " enableSync: false (default) -> single-server, no coherence layer.",
                " Turn it on ONLY on a multi-server network that SHARES a database.",
                "",
                " When enabled, the source of change signals is chosen as:",
                "   1. a 'redis:' block below (works on ANY backend)         -> Redis pub/sub",
                "   2. otherwise a backend with a native change feed (mongo, -> native feed",
                "      postgresql)",
                "   3. otherwise NO-OP (add a redis block for those backends)",
                "",
                " With enableSync on, a versioned entity (PlayerData/PDSection)",
                " routed to a backend that cannot enforce the optimistic lock",
                " (groupedfile, localfile, h2, memory) is REJECTED at boot -",
                " route it to sql | postgresql | mongo.",
                "",
                " Optional single app-level Redis/Valkey block (never one per",
                " backend). Add it below to route signals through Redis pub/sub:",
                "",
                "   redis:",
                "     host: \"localhost\"",
                "     port: 6379",
                "     #user: \"\"      # Redis 6+ ACL user (optional)",
                "     #pass: \"\"      # AUTH password (optional)",
                "     #database: 0",
                "     #channel: \"everydatabase:changes\"  # isolate apps on a shared server",
                "     #ssl: false",
                "============================================================"));

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
     * Appends the {@code multiplatform-accounts} block to an ALREADY EXISTING storage.yml that
     * predates it (a fresh file gets it at the top via {@link #writeDefault(File)}).
     *
     * @return true when the block was written (file saved); false when it already existed
     */
    public static boolean ensureMultiplatformAccounts(Config config) {
        if (config.contains("multiplatform-accounts")) {
            return false;
        }
        writeMultiplatformAccountsBlock(config);
        config.save();
        return true;
    }

    private static void writeMultiplatformAccountsBlock(Config config) {
        config.setValue("multiplatform-accounts.enabled", false);
        config.setValue("multiplatform-accounts.backend", "");
        config.setComment("multiplatform-accounts", String.join("\n",
                "============================================================",
                " Multi-Platform Accounts",
                "",
                " Links identities from different platforms into ONE account:",
                " Minecraft <-> Hytale <-> external providers (Discord, a",
                " website, ...). Linked players share the account-wide data",
                " (network achievements, VIP status, ...).",
                "",
                " Enabling this writes NOTHING by itself - account rows only",
                " start to exist when identities are actually linked",
                " (/account link). On a real network the backend below must",
                " be a database SHARED by every instance (MariaDB/Mongo/...),",
                " never a local file backend.",
                "============================================================"));
        config.setComment("multiplatform-accounts.backend",
                "Backend hosting the WHOLE account family (account registry + account-wide"
                        + " sections). Empty = the 'default-backend'.");
    }
}
