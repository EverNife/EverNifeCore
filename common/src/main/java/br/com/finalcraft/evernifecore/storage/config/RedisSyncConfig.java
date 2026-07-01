package br.com.finalcraft.evernifecore.storage.config;

/**
 * The single, app-level {@code redis:} block of storage.yml used by the cache-sync
 * layer. When present, cross-instance cache coherence is driven through a Redis/Valkey
 * pub/sub transport (regardless of the data backend); when absent, coherence falls back
 * to a native change feed (Mongo/PostgreSQL) or to a no-op.
 *
 * <p>There is at most ONE redis block per app (never one per backend): it is only a
 * signalling channel, not a data store. Immutable; produced by {@link StorageYamlParser}.</p>
 */
public final class RedisSyncConfig {

    public static final int DEFAULT_PORT = 6379;

    private final String host;
    private final int port;
    private final String username;   // nullable: no ACL user
    private final String password;   // nullable: no AUTH
    private final int database;
    private final String channel;    // nullable: transport default
    private final boolean ssl;

    RedisSyncConfig(String host, int port, String username, String password,
                    int database, String channel, boolean ssl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.channel = channel;
        this.ssl = ssl;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /** The ACL username (Redis 6+), or {@code null} when none. */
    public String getUsername() {
        return username;
    }

    /** The AUTH password, or {@code null} when the server has no auth. */
    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    /** Nullable - the transport uses its own default channel when absent. */
    public String getChannel() {
        return channel;
    }

    public boolean isSsl() {
        return ssl;
    }
}
