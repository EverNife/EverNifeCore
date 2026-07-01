package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.RedisSyncConfig;
import br.com.finalcraft.evernifecore.storage.config.SyncTransportMode;
import br.com.finalcraft.everydatabase.changefeed.ChangeFeedStorage;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.sync.CacheSync;
import br.com.finalcraft.everydatabase.manager.sync.CacheSyncTransport;

import java.util.List;
import java.util.function.Consumer;

/**
 * Wires cross-instance cache coherence ({@link CacheSync}) for the PlayerData/PDSection managers,
 * per storage.yml's {@code multi-server-cache-sync} block. Semantics:
 *
 * <ul>
 *   <li>{@code enabled: false} -> NO-OP: never starts anything.</li>
 *   <li>{@code transport: auto} (default) -> Redis if the redis block is enabled, else the backends'
 *       native change feed (Mongo/PostgreSQL) when EVERY manager's backend has one, else a silent
 *       no-op.</li>
 *   <li>{@code transport: redis} -> force the Redis pub/sub transport (silent no-op if no redis block
 *       is enabled).</li>
 *   <li>{@code transport: native} -> use only the backends' native feed; silent no-op if any backend
 *       has none.</li>
 * </ul>
 *
 * <p>The no-feed / no-redis case is a DELIBERATELY silent no-op: a feedless backend with no redis
 * simply has no coherence, which is the correct and harmless outcome on a single server (starting a
 * perpetual poller would be waste, and {@code CacheSync.start()} throws for a feedless backend with
 * no poll interval anyway). Only an EXPLICIT redis request that then fails is surfaced.</p>
 *
 * <p>The Redis transport lives in the optional {@code everydatabase-manager-jedis} module, which is
 * not a compile dependency of {@code common}; it is loaded reflectively so {@code common} builds
 * without it and the redis path fails loudly at boot when the runtime jar is missing.</p>
 *
 * <p>{@code CacheSync.close()} deliberately does NOT close its transport, so the wiring hands the
 * caller a {@link Handle} owning BOTH - closing the handle (on shutdown or a re-wire after a runtime
 * transfer) releases the Redis connection and its subscriber thread instead of leaking them.</p>
 */
public final class CacheSyncWiring {

    private static final String JEDIS_TRANSPORT = "br.com.finalcraft.everydatabase.manager.sync.jedis.JedisCacheSyncTransport";
    private static final String JEDIS_CONFIG = "br.com.finalcraft.everydatabase.manager.sync.jedis.JedisCacheSyncConfig";

    /** A started sync plus the transport it runs over; {@link #close()} releases both. */
    public static final class Handle implements AutoCloseable {
        private final CacheSync sync;
        private final CacheSyncTransport transport; //null on the native-feed path

        private Handle(CacheSync sync, CacheSyncTransport transport) {
            this.sync = sync;
            this.transport = transport;
        }

        @Override
        public void close() {
            try { sync.close(); } catch (RuntimeException ignored) { }
            closeTransport(transport);
        }
    }

    private CacheSyncWiring() {
    }

    /**
     * Starts a {@link CacheSync} for {@code managers} according to {@code parsed}, or returns
     * {@code null} when sync is disabled / not applicable (a clean no-op). {@code logWarning} receives
     * a human-readable note whenever the wiring is skipped for a reason worth surfacing.
     */
    public static Handle startIfEnabled(ParsedStorageConfig parsed,
                                        List<CachingManager<?, ?>> managers,
                                        Consumer<String> logInfo,
                                        Consumer<String> logWarning) {
        if (!parsed.isEnableSync() || managers.isEmpty()) {
            return null;   // sync off, or nothing to keep coherent
        }

        SyncTransportMode mode = parsed.getTransportMode();
        RedisSyncConfig redis = parsed.getRedisSync();   // non-null only when the redis block is enabled

        // Redis path: taken on AUTO (when a redis block is enabled) or REDIS (forced).
        if (redis != null && (mode == SyncTransportMode.AUTO || mode == SyncTransportMode.REDIS)) {
            CacheSyncTransport transport = buildRedisTransport(redis, logWarning);
            if (transport == null) {
                return null;   // an EXPLICIT redis request failed - already surfaced (missing jedis / connect error)
            }
            Handle handle = bindAndStart(CacheSync.auto().via(transport), transport, managers, logWarning);
            if (handle != null) {
                logInfo.accept("Cache-sync enabled via Redis transport (" + redis.getHost() + ":" + redis.getPort() + ").");
            }
            return handle;
        }

        // Native-feed path: taken on AUTO (no redis) or NATIVE, and only when EVERY backend has a
        // native feed. Otherwise a DELIBERATELY silent no-op (no perpetual poller, no nagging): a
        // feedless backend with no redis simply has no coherence, which is fine on a single server.
        if (mode == SyncTransportMode.REDIS || !allBackendsHaveNativeFeed(managers)) {
            return null;
        }
        Handle handle = bindAndStart(CacheSync.auto(), null, managers, logWarning);
        if (handle != null) {
            logInfo.accept("Cache-sync enabled via the backends' native change feeds.");
        }
        return handle;
    }

    /** Binds + starts, releasing sync AND transport if anything throws (a half-wired sync must not leak). */
    private static Handle bindAndStart(CacheSync sync, CacheSyncTransport transport,
                                       List<CachingManager<?, ?>> managers, Consumer<String> logWarning) {
        try {
            for (CachingManager<?, ?> manager : managers) {
                sync.bind(manager);
            }
            sync.start();
            return new Handle(sync, transport);
        } catch (RuntimeException startFailure) {
            try { sync.close(); } catch (RuntimeException ignored) { }
            closeTransport(transport);
            logWarning.accept("Failed to start cache-sync - it is a NO-OP for this session. ("
                    + startFailure + ")");
            return null;
        }
    }

    private static void closeTransport(CacheSyncTransport transport) {
        if (transport instanceof AutoCloseable) {
            try { ((AutoCloseable) transport).close(); } catch (Exception ignored) { }
        }
    }

    private static boolean allBackendsHaveNativeFeed(List<CachingManager<?, ?>> managers) {
        for (CachingManager<?, ?> manager : managers) {
            if (!(manager.storage() instanceof ChangeFeedStorage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reflectively builds a {@link CacheSyncTransport} over Redis. Returns {@code null} (after
     * surfacing the reason) when the optional jedis module or the redis server is unavailable.
     */
    private static CacheSyncTransport buildRedisTransport(RedisSyncConfig redis, Consumer<String> logWarning) {
        try {
            Class<?> configClass = Class.forName(JEDIS_CONFIG);
            Object builder = configClass.getMethod("builder", String.class, int.class)
                    .invoke(null, redis.getHost(), redis.getPort());
            Class<?> builderClass = builder.getClass();
            if (redis.getUsername() != null) {
                builder = builderClass.getMethod("username", String.class).invoke(builder, redis.getUsername());
            }
            if (redis.getPassword() != null) {
                builder = builderClass.getMethod("password", String.class).invoke(builder, redis.getPassword());
            }
            builder = builderClass.getMethod("database", int.class).invoke(builder, redis.getDatabase());
            builder = builderClass.getMethod("ssl", boolean.class).invoke(builder, redis.isSsl());
            if (redis.getChannel() != null) {
                builder = builderClass.getMethod("channel", String.class).invoke(builder, redis.getChannel());
            }
            Object config = builderClass.getMethod("build").invoke(builder);

            Class<?> transportClass = Class.forName(JEDIS_TRANSPORT);
            Object transport = transportClass.getMethod("connect", configClass).invoke(null, config);
            return (CacheSyncTransport) transport;
        } catch (ClassNotFoundException missingModule) {
            logWarning.accept("A 'redis:' block is configured but the 'everydatabase-manager-jedis'"
                    + " runtime is not on the classpath - cache-sync via Redis is a NO-OP. (Add the"
                    + " jedis dependency to enable it.)");
            return null;
        } catch (ReflectiveOperationException reflectionFailure) {
            Throwable cause = reflectionFailure.getCause() != null ? reflectionFailure.getCause() : reflectionFailure;
            logWarning.accept("Failed to initialize the Redis cache-sync transport - cache-sync is a"
                    + " NO-OP. (" + cause + ")");
            return null;
        }
    }
}
