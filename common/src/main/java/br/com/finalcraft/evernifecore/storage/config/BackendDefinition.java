package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.everydatabase.Storage;
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

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

/**
 * A logical backend parsed from the {@code backends:} section of storage.yml.
 * Immutable; created by {@link StorageYamlParser}.
 *
 * <p>{@link #createStorage(StorageLogConfig)} instantiates the matching EveryDatabase
 * {@link Storage} using each backend's TYPED constructor - never the generic
 * {@code Storages.create(StorageConfig)} (which always picks the MySQL dialect for
 * any SqlConfig).</p>
 */
public final class BackendDefinition {

    /** File format for LOCALFILE backends. JSON in localfile is always pretty. */
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
}
