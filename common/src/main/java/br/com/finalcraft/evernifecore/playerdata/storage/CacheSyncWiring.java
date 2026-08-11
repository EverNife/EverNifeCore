package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.RedisSyncConfig;
import br.com.finalcraft.evernifecore.storage.config.SyncTransportMode;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.changefeed.ChangeFeedStorage;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.modules.groupedfile.GroupedFileStorage;
import br.com.finalcraft.everydatabase.modules.localfile.LocalFileStorage;
import br.com.finalcraft.everydatabase.manager.sync.CacheSync;
import br.com.finalcraft.everydatabase.manager.sync.CacheSyncTransport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Wires cross-instance cache coherence ({@link CacheSync}) for the PlayerData/PDSection managers,
 * per storage.yml's {@code multi-server-cache-sync} block. Semantics:
 *
 * <ul>
 *   <li>{@code enabled: false} -> NO-OP: never starts anything.</li>
 *   <li>{@code transport: auto} (default) -> Redis if the redis block is enabled, else the native
 *       change feed of every manager whose backend has one and is not a file backend.</li>
 *   <li>{@code transport: redis} -> force the Redis pub/sub transport (silent no-op if no redis block
 *       is enabled). It never degrades into the native feed: the admin named a transport.</li>
 *   <li>{@code transport: native} -> use only the backends' native feed. This is the only way to reach
 *       a FILE backend's feed, which watches the local filesystem: it cannot carry another server's
 *       write, so it buys nothing on a network and is worth its watcher thread only where an admin
 *       edits the data files by hand.</li>
 * </ul>
 *
 * <p>The native path selects <b>per manager</b>, not per server: {@link CacheSync#auto()} routes each
 * bound manager by its own storage, so a collection on a feedless backend is simply left unbound and
 * costs the others nothing. Coverage is reported - full coverage stays quiet, partial coverage names
 * what was left out, and zero coverage is silent (the stock single-server install, where no coherence
 * is the correct outcome). Only an EXPLICIT redis request that then fails is surfaced as a failure.
 *
 * <p>The Redis transport lives in the optional {@code everydatabase-manager-jedis} module and is
 * loaded reflectively, so {@code common} builds without it and the redis path fails loudly at boot
 * when the runtime jar is missing.
 *
 * <p>{@code CacheSync.close()} does NOT close its transport, so the wiring hands back a {@link Handle}
 * owning BOTH - closing it releases the Redis connection and its subscriber thread.
 */
public final class CacheSyncWiring {

    private static final String JEDIS_TRANSPORT = "br.com.finalcraft.everydatabase.manager.sync.jedis.JedisCacheSyncTransport";
    private static final String JEDIS_CONFIG = "br.com.finalcraft.everydatabase.manager.sync.jedis.JedisCacheSyncConfig";

    /** How many collection names a coverage report prints before summarizing the rest as a count. */
    private static final int MAX_LISTED_COLLECTIONS = 8;

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
     * Starts a {@link CacheSync} over the applicable subset of {@code managers} according to
     * {@code parsed}, or returns {@code null} when sync is disabled / nothing applies (a clean no-op).
     * {@code logInfo} gets the resulting coverage, {@code logWarning} the collections left without any
     * cross-instance signal and an explicit request that failed.
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

        if (mode == SyncTransportMode.REDIS) {
            return null;   // an explicit redis request never quietly degrades into the native feed
        }

        // Native-feed path: taken on AUTO (no redis) or NATIVE, over the managers whose OWN backend
        // can push. A feedless one is left unbound rather than disabling the path for everybody: it
        // ends up with the coherence it would have had anyway, and the rest keep theirs.
        List<CachingManager<?, ?>> feedCapable = nativeFeedManagers(managers, mode);
        if (feedCapable.isEmpty()) {
            // DELIBERATELY silent (no perpetual poller, no nagging): nothing here can push, which is
            // the stock single-server install - a groupedfile install under the default transport.
            return null;
        }
        Handle handle = bindAndStart(CacheSync.auto(), null, feedCapable, logWarning);
        if (handle != null) {
            logInfo.accept("Cache-sync enabled via the backends' native change feeds ("
                    + feedCapable.size() + " of " + managers.size() + " collections).");
            reportUncovered(managers, feedCapable, logInfo, logWarning);
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

    /** The managers whose own backend can push a change feed, under the transport {@code mode}. */
    private static List<CachingManager<?, ?>> nativeFeedManagers(List<CachingManager<?, ?>> managers,
                                                                 SyncTransportMode mode) {
        List<CachingManager<?, ?>> selected = new ArrayList<>();
        for (CachingManager<?, ?> manager : managers) {
            Storage storage = manager.storage();
            if (!(storage instanceof ChangeFeedStorage)) {
                continue;
            }
            if (mode == SyncTransportMode.AUTO && isFileBacked(storage)) {
                continue;
            }
            selected.add(manager);
        }
        return selected;
    }

    /**
     * Whether a backend's change feed watches the local filesystem. Such a feed is real, but it is a
     * single-machine signal, and it reports this server's own writes back to it - so it is worth a
     * thread only when the admin asked for it by name.
     */
    private static boolean isFileBacked(Storage storage) {
        return storage instanceof LocalFileStorage || storage instanceof GroupedFileStorage;
    }

    /**
     * Names what the native path left unbound, so partial coverage is never read as full coverage. The
     * two reasons need different answers, so they are reported apart: a feedless backend is a hole only
     * Redis closes, while a file backend whose feed was skipped is working as intended.
     */
    private static void reportUncovered(List<CachingManager<?, ?>> all,
                                        List<CachingManager<?, ?>> covered,
                                        Consumer<String> logInfo,
                                        Consumer<String> logWarning) {
        Set<CachingManager<?, ?>> bound = Collections.newSetFromMap(new IdentityHashMap<>());
        bound.addAll(covered);

        List<String> feedless = new ArrayList<>();
        List<String> skippedFileFeed = new ArrayList<>();
        for (CachingManager<?, ?> manager : all) {
            if (bound.contains(manager)) {
                continue;
            }
            Storage storage = manager.storage();
            boolean hasFeed = storage instanceof ChangeFeedStorage;
            (hasFeed && isFileBacked(storage) ? skippedFileFeed : feedless).add(manager.collection());
        }

        if (!feedless.isEmpty()) {
            logWarning.accept("Cache-sync leaves " + feedless.size() + " collection(s) INCOHERENT across"
                    + " instances - their backend has no change feed, so another server's write to them"
                    + " is not seen here: " + preview(feedless) + ". Configure a 'redis:' block, which"
                    + " covers every backend type.");
        }
        if (!skippedFileFeed.isEmpty()) {
            logInfo.accept("Cache-sync skipped " + skippedFileFeed.size() + " collection(s) on a file"
                    + " backend, whose feed only reports this machine's own writes: "
                    + preview(skippedFileFeed) + ". Set 'transport: native' if you edit those files by"
                    + " hand.");
        }
    }

    /** A comma-joined preview, capped so a server with dozens of sections does not print a wall. */
    private static String preview(List<String> collections) {
        int shown = Math.min(collections.size(), MAX_LISTED_COLLECTIONS);
        String head = String.join(", ", collections.subList(0, shown));
        return collections.size() > shown
                ? head + " and " + (collections.size() - shown) + " more"
                : head;
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
