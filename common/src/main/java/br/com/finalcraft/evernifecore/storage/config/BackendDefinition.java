package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.modules.groupedfile.GroupedFileConfig;
import br.com.finalcraft.everydatabase.modules.groupedfile.GroupedFileStorage;
import br.com.finalcraft.everydatabase.modules.localfile.LocalFileConfig;
import br.com.finalcraft.everydatabase.modules.localfile.LocalFileStorage;
import br.com.finalcraft.everydatabase.modules.memory.InMemoryStorage;
import br.com.finalcraft.everydatabase.modules.mongo.MongoConfig;
import br.com.finalcraft.everydatabase.modules.mongo.MongoStorage;
import br.com.finalcraft.everydatabase.modules.sql.PoolTuning;
import br.com.finalcraft.everydatabase.modules.sql.SqlConfig;
import br.com.finalcraft.everydatabase.modules.sql.SqlStorage;
import br.com.finalcraft.everydatabase.modules.sql.h2.H2SqlStorage;
import br.com.finalcraft.everydatabase.modules.sql.postgresql.PostgreSqlStorage;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A logical backend: the connection target and wire format a {@link Storage} is opened from. Parsed
 * from a storage.yml {@code storage-backends.<id>} entry or an inline {@code storage.<type>} block by
 * {@link StorageYamlParser}, or built in code with the {@code groupedFile}/{@code sql}/... factories.
 * Immutable.
 *
 * <p>{@link #createStorage(StorageLogConfig)} instantiates the matching EveryDatabase
 * {@link Storage} using each backend's TYPED constructor - never the generic
 * {@code Storages.create(StorageConfig)} (which always picks the MySQL dialect for
 * any SqlConfig).</p>
 *
 * <p>{@link #equals(Object)} is VALUE equality over the connection target and wire format
 * ({@code type, url, user, pass, path, format, database} and the pool scalars), so two definitions
 * that resolve the same physical store compare equal - the check {@code ECStorage.openOrReload} uses to
 * decide whether a reload can reuse the live connection. The routing metadata {@code name} and
 * {@code enabled} are deliberately NOT part of identity.</p>
 *
 * <p>Implements {@link ConfigLifecycle} so it participates in EveryConfig's read/write hooks when bound
 * as a config value; the hooks default to no-ops here.</p>
 */
public final class BackendDefinition implements ConfigLifecycle {

    /** Wire format for the file backends (LOCALFILE and GROUPEDFILE). JSON on a file backend is always pretty. */
    public enum FileFormat { YAML, JSON }

    private final String name;
    private final boolean enabled;
    private final BackendType type;

    // sql | postgresql | h2 | mongo
    private final String url;
    // sql | postgresql | h2
    private final String user;
    private final String pass;
    private final PoolTuning poolTuning;
    // localfile
    private final String path;
    private final FileFormat format;
    // mongo
    private final String database;

    BackendDefinition(String name, boolean enabled, BackendType type,
                      String url, String user, String pass, PoolTuning poolTuning,
                      String path, FileFormat format, String database) {
        this.name = name;
        this.enabled = enabled;
        this.type = type;
        this.url = url;
        this.user = user;
        this.pass = pass;
        this.poolTuning = poolTuning;
        this.path = path;
        this.format = format;
        this.database = database;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public BackendType getType() {
        return type;
    }

    /** Meaningful for LOCALFILE and GROUPEDFILE backends; YAML by default. */
    public FileFormat getFormat() {
        return format;
    }

    /** File backends (LOCALFILE/GROUPEDFILE): the data directory; {@code null} otherwise. */
    public String getPath() {
        return path;
    }

    /** SQL/POSTGRESQL/H2/MONGO: the connection URL; {@code null} for file/memory backends. */
    public String getUrl() {
        return url;
    }

    /** SQL/POSTGRESQL/H2: the connection user; {@code null}/empty otherwise. */
    public String getUser() {
        return user;
    }

    /** SQL/POSTGRESQL/H2: the connection password; {@code null}/empty otherwise. */
    public String getPass() {
        return pass;
    }

    /** MONGO: the database name; {@code null} otherwise. */
    public String getDatabase() {
        return database;
    }

    /** SQL/POSTGRESQL/H2: the HikariCP pool tuning, or {@code null} to use the driver defaults. */
    public PoolTuning getPoolTuning() {
        return poolTuning;
    }

    // ---------------------------------------------------------------------
    // Factories (build a definition in code, e.g. a plugin's standardized default)
    // ---------------------------------------------------------------------

    /** A GROUPEDFILE backend (one file per key) rooted at {@code path}, {@code null} format = YAML. */
    public static BackendDefinition groupedFile(String path, FileFormat format) {
        return of(BackendType.GROUPEDFILE.getId(), true, BackendType.GROUPEDFILE,
                null, null, null, null, null, null, null, path, format, null);
    }

    /** A LOCALFILE backend (one file per entity) rooted at {@code path}, {@code null} format = YAML. */
    public static BackendDefinition localFile(String path, FileFormat format) {
        return of(BackendType.LOCALFILE.getId(), true, BackendType.LOCALFILE,
                null, null, null, null, null, null, null, path, format, null);
    }

    /** An H2 backend at {@code url} (e.g. {@code jdbc:h2:file:./plugins/MyPlugin/Data/h2database}). */
    public static BackendDefinition h2(String url) {
        return of(BackendType.H2.getId(), true, BackendType.H2,
                url, null, null, null, null, null, null, null, null, null);
    }

    /** A MySQL/MariaDB backend. */
    public static BackendDefinition sql(String url, String user, String pass) {
        return of(BackendType.SQL.getId(), true, BackendType.SQL,
                url, user, pass, null, null, null, null, null, null, null);
    }

    /** A PostgreSQL backend. */
    public static BackendDefinition postgresql(String url, String user, String pass) {
        return of(BackendType.POSTGRESQL.getId(), true, BackendType.POSTGRESQL,
                url, user, pass, null, null, null, null, null, null, null);
    }

    /** A MongoDB backend. */
    public static BackendDefinition mongo(String url, String database) {
        return of(BackendType.MONGO.getId(), true, BackendType.MONGO,
                url, null, null, null, null, null, null, null, null, database);
    }

    /** An ephemeral in-memory backend (tests / throwaway). */
    public static BackendDefinition memory() {
        return of(BackendType.MEMORY.getId(), true, BackendType.MEMORY,
                null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * The default storage codec for {@code type} on this backend. Returns the {@link ConfigFactoryCodec}
     * bridge, so every entity that does not opt out of it carries the ConfigFactory type authority and the
     * ConfigLifecycle hooks into storage. A file backend picks its wire format from {@link #getFormat()}
     * (YAML by default, else pretty JSON - a human may open the file); every other backend uses compact
     * JSON (SQL/Mongo/InMemory parse the payload as JSON).
     *
     * <p>Both PlayerData ({@code BindingResolver}/{@code PlayerDataBinding}/the account layer) and
     * plugin-owned inline backends ({@link br.com.finalcraft.evernifecore.storage.ECStorage}) route
     * through here, so a config's {@code format} maps to the same codec everywhere.</p>
     */
    public <V> Codec<V> defaultCodec(Class<V> type) {
        if (this.type == BackendType.LOCALFILE || this.type == BackendType.GROUPEDFILE) {
            return format == FileFormat.YAML
                    ? ConfigFactoryCodec.yaml(type)
                    : ConfigFactoryCodec.jsonPretty(type);   // json on a file backend is always pretty
        }
        return ConfigFactoryCodec.json(type);                // every other backend: compact JSON
    }

    /**
     * Instantiates the EveryDatabase Storage for this backend. Does NOT connect -
     * connections are opened by {@code storage.init()} (the registry's initAll).
     */
    public Storage createStorage(StorageLogConfig logConfig) {
        switch (type) {
            case LOCALFILE:
                // LocalFileConfig(path) = prettyPrint enabled
                return new LocalFileStorage(new LocalFileConfig(Paths.get(path)), logConfig);
            case GROUPEDFILE:
                // key-major: one file per key holding all its collections; the format follows the codec
                return new GroupedFileStorage(new GroupedFileConfig(Paths.get(path)), logConfig);
            case SQL:
                ensureSqlBackendAvailable();
                ensureJdbcDriver("com.mysql.cj.jdbc.Driver");
                return new SqlStorage(sqlConfig(), logConfig);
            case POSTGRESQL:
                ensureJdbcDriver("org.postgresql.Driver");
                return new PostgreSqlStorage(sqlConfig(), logConfig);
            case H2:
                ensureJdbcDriver("org.h2.Driver");
                return new H2SqlStorage(sqlConfig(), logConfig);
            case MONGO:
                return new MongoStorage(new MongoConfig(url, database, Optional.empty()), logConfig);
            case MEMORY:
                return new InMemoryStorage(logConfig);
            default:
                throw new StorageConfigException("Backend '" + name + "' has unsupported type " + type);
        }
    }

    /**
     * Registers the JDBC driver with the {@link java.sql.DriverManager}. Needed because
     * the relocated driver is downloaded via libby AFTER the DriverManager's static
     * ServiceLoader scan has already run, so it is never registered automatically -
     * Hikari would fail with "No suitable driver". Loading the class triggers its
     * static initializer, which performs the registration.
     *
     * <p>The literal here is REWRITTEN by the minecraft shadowJar relocation to the
     * relocated package ({@code br.com.finalcraft.everydatabase.libs.*}) - the same
     * string rewrite that bStats relies on - and stays original in tests/standalone,
     * so it resolves in both cases.</p>
     */
    private static void ensureJdbcDriver(String driverClassName) {
        try {
            Class.forName(driverClassName);
        } catch (Throwable notOnClasspath) {
            // This backend's deps may not have been downloaded; the storage init() below
            // surfaces a clearer "Failed to initialize backend" error if the driver really is missing.
            // A FINE trace naming the driver still helps diagnose that later "No suitable driver".
            Logger.getLogger("EverNifeCore").log(Level.FINE,
                    "JDBC driver '" + driverClassName + "' is not on the classpath yet", notOnClasspath);
        }
    }

    /**
     * Fail fast on the {@code sql} (MySQL/MariaDB) backend when running on Hytale. The MySQL
     * connector is GPL-licensed and is NOT bundled in the Hytale jar, and Hytale has no runtime
     * dependency loader to fetch it - so {@link #ensureJdbcDriver} would only leave a later,
     * cryptic "No suitable driver". This turns it into a clear config error at open time. The
     * platform id is resolved defensively (like {@code Accounts.platformProvider}) so a pure-JUnit
     * runtime - whose platform id is {@code "test"} - never trips it.
     */
    private void ensureSqlBackendAvailable() {
        String platformId = null;
        try {
            IPlatform platform = EverNifeCore.getPlatform();
            if (platform != null) {
                platformId = platform.getPlatformProviderId();
            }
        } catch (Throwable platformNotRegistered) {
            //early boot / standalone: no platform registered - assume the backend is allowed
        }
        if ("hytale".equals(platformId)) {
            throw new StorageConfigException("The 'sql' (MySQL/MariaDB) storage backend is not available on Hytale: the MySQL JDBC driver "
                    + "is GPL-licensed and is not bundled in the Hytale jar, and Hytale has no runtime dependency "
                    + "loader to fetch it. Use one of: postgresql, h2, mongo, groupedfile, localfile or memory.");
        }
    }

    private SqlConfig sqlConfig() {
        return poolTuning != null
                ? new SqlConfig(url, user, pass, poolTuning)
                : new SqlConfig(url, user, pass);
    }

    // ---------------------------------------------------------------------
    // Factory used by the parser
    // ---------------------------------------------------------------------

    static BackendDefinition of(String name, boolean enabled, BackendType type,
                                String url, String user, String pass,
                                Integer poolMinIdle, Integer poolMaxSize,
                                Integer poolConnectTimeoutSeconds, Integer poolIdleTimeoutSeconds,
                                String path, FileFormat format, String database) {
        switch (type) {
            case SQL:
            case POSTGRESQL:
            case H2:
                requireField(name, "url", url);
                break;
            case MONGO:
                requireField(name, "url", url);
                requireField(name, "db", database);
                break;
            case LOCALFILE:
            case GROUPEDFILE:
                requireField(name, "path", path);
                break;
            case MEMORY:
                break;
        }

        PoolTuning poolTuning = null;
        if (poolMinIdle != null || poolMaxSize != null
                || poolConnectTimeoutSeconds != null || poolIdleTimeoutSeconds != null) {
            poolTuning = new PoolTuning(
                    poolMinIdle != null ? poolMinIdle : 2,
                    poolMaxSize != null ? poolMaxSize : 10,
                    Duration.ofSeconds(poolConnectTimeoutSeconds != null ? poolConnectTimeoutSeconds : 5),
                    Duration.ofSeconds(poolIdleTimeoutSeconds != null ? poolIdleTimeoutSeconds : 30)
            );
        }

        return new BackendDefinition(name, enabled, type, url, user, pass, poolTuning,
                path, format != null ? format : FileFormat.YAML, database);
    }

    private static void requireField(String backendName, String field, String value) {
        if (value == null || value.isEmpty()) {
            throw new StorageConfigException("Backend '" + backendName + "' is missing the required"
                    + " field '" + field + "' in storage.yml!");
        }
    }

    // ---------------------------------------------------------------------
    // Value identity (connection target + wire format; NOT name/enabled)
    // ---------------------------------------------------------------------

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BackendDefinition)) {
            return false;
        }
        BackendDefinition that = (BackendDefinition) other;
        return type == that.type
                && format == that.format
                && Objects.equals(url, that.url)
                && Objects.equals(user, that.user)
                && Objects.equals(pass, that.pass)
                && Objects.equals(path, that.path)
                && Objects.equals(database, that.database)
                && poolTuningEquals(poolTuning, that.poolTuning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, format, url, user, pass, path, database, poolTuningHash(poolTuning));
    }

    /**
     * {@link PoolTuning} carries no {@code equals}, so compare its scalar fields - two definitions parsed
     * from the same config build distinct PoolTuning instances that must still compare equal (else the
     * openOrReload reuse path would never fire for a pooled backend).
     */
    private static boolean poolTuningEquals(PoolTuning a, PoolTuning b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.minIdle() == b.minIdle()
                && a.maxSize() == b.maxSize()
                && Objects.equals(a.connectTimeout(), b.connectTimeout())
                && Objects.equals(a.idleTimeout(), b.idleTimeout())
                && Objects.equals(a.maxLifetime(), b.maxLifetime());
    }

    private static int poolTuningHash(PoolTuning tuning) {
        return tuning == null ? 0
                : Objects.hash(tuning.minIdle(), tuning.maxSize(),
                        tuning.connectTimeout(), tuning.idleTimeout(), tuning.maxLifetime());
    }
}
