package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.evernifecore.playerdata.storage.PdSyncBindGuard;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import br.com.finalcraft.everydatabase.manager.log.ManagerLog;
import br.com.finalcraft.everydatabase.manager.writeback.ConflictHooks;
import br.com.finalcraft.everydatabase.manager.writeback.FlushMode;
import br.com.finalcraft.everydatabase.manager.writeback.WriteBackFlusher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * The storage of network-wide server cooldowns: a collection of {@link ServerCooldownRow}, keyed by
 * cooldown identifier, on the shared backend. A mutable instance behind a static facade, mirroring the
 * account layer - an instance is bootstrapped once (from the PlayerController bootstrap) and swapped in
 * atomically.
 *
 * <p><b>Backend.</b> The rows live on the same shared backend the account family uses
 * ({@code multi-platform-accounts.storage-backend-id ?? default-backend}); a cooldown that spans the network has
 * exactly the reach an account has, so it needs the same one backend every instance agrees on - not one
 * of its own.</p>
 *
 * <p><b>Warm at bind.</b> The whole collection is preloaded on bootstrap: it is tiny (one row per
 * network cooldown id that a plugin actually declares), and it is what keeps a cooldown check a cache
 * read rather than backend I/O on the server thread.</p>
 *
 * <p><b>Convergence.</b> Rows are versioned, so two servers starting the same cooldown at once make one
 * of them lose the write; the loser resolves the race by {@link CooldownEntry#latest} against the state
 * that won, and retries - never by discarding a side wholesale.</p>
 */
public final class ServerCooldowns {

    /** The collection the network-wide server cooldowns live in, on the shared backend. */
    public static final String COLLECTION = "ec_server_cooldowns";

    /** Owner tag of this collection's registry claim. */
    private static final String CLAIM_OWNER = "EverNifeCore:ServerCooldowns";

    /** Human-readable id of the entity kind, for the flush's logs and exceptions. */
    private static final String WHAT = "ServerCooldown";

    private static volatile ServerCooldowns INSTANCE;

    private final String backendName;
    private final CachingManager<String, ServerCooldownRow> manager;
    private final WriteBackFlusher flusher;

    //writes fired outside the flush tick (a mutation persists right away); the shutdown/reload barrier
    //awaits these before it closes the backend, so a write still on its way is never cut off
    private final Set<CompletableFuture<Void>> pendingWrites = ConcurrentHashMap.newKeySet();

    private ServerCooldowns(String backendName, CachingManager<String, ServerCooldownRow> manager, ManagerLog log) {
        this.backendName = backendName;
        this.manager = manager;
        this.flusher = new WriteBackFlusher(log);
    }

    /** True when the network cooldown storage is bootstrapped. */
    public static boolean isEnabled() {
        return INSTANCE != null;
    }

    /** The network cooldown storage, or {@code null} before it is bootstrapped. */
    public static ServerCooldowns get() {
        return INSTANCE;
    }

    /**
     * Builds the manager against storage.yml, warms the collection and installs it as the current
     * instance. Called by the PlayerController bootstrap once the registry is initialized.
     *
     * @param log where the bind report and the flush's conflict/failure lines go
     */
    public static ServerCooldowns bootstrap(ParsedStorageConfig parsed, StorageRegistry registry,
                                            RefRegistry globalRegistry, ManagerLog log) {
        String backendName = parsed.getAccountBackendName();
        Storage storage = registry.get(backendName);
        EntityDescriptor<String, ServerCooldownRow> descriptor = descriptor(parsed, backendName);

        if (!registry.claimCollection(backendName, COLLECTION, CLAIM_OWNER)) {
            throw new StorageConfigException("Server cooldowns want collection '" + COLLECTION
                    + "' on backend '" + backendName + "', but it is already used by '"
                    + registry.getCollectionOwner(backendName, COLLECTION) + "'!");
        }

        //a network cooldown is written from every server that declares it: reject/warn a backend that
        //cannot enforce the optimistic lock, on the same signal the account family carries
        List<String> warnings = new ArrayList<>();
        PdSyncBindGuard.check("Server cooldowns", descriptor, storage, parsed,
                parsed.isMultiplatformAccountsEnabled(), warnings);
        for (String warning : warnings) {
            log.log(Level.WARNING, warning);
        }

        CachingManager<String, ServerCooldownRow> manager =
                globalRegistry.manager(descriptor, storage, CachePolicy.always());
        //warm the whole collection: the set is tiny, and a cooldown check reads it synchronously
        manager.preloadAll().join();

        ServerCooldowns fresh = new ServerCooldowns(backendName, manager, log);
        INSTANCE = fresh;
        if (storage.enforcesOptimisticLock()){
            log.log(Level.INFO, "Bound the network server cooldowns (collection '" + COLLECTION
                    + "' on shared backend '" + backendName + "', " + manager.cachedSize() + " warm).");
        } else {
            //a backend that cannot enforce the lock is not a shared one: with no declared multi-instance
            //intent (no enabled redis block; PdSyncBindGuard only warns, never aborts) the network
            //reach collapses onto this single server - correct, since one server IS the whole network
            log.log(Level.INFO, "Bound the network server cooldowns on local backend '" + backendName
                    + "' (" + manager.cachedSize() + " warm). No shared backend is configured, so the"
                    + " NETWORK reach collapses onto this single server - a network cooldown behaves"
                    + " exactly like a local one until a shared backend is set up.");
        }
        return fresh;
    }

    /** Clears the current instance (on shutdown / a failed reload). */
    public static void clear() {
        INSTANCE = null;
    }

    /**
     * Re-installs a previously captured instance. The PlayerController bootstrap publishes the fresh
     * storage while it boots; when that boot FAILS its registry is closed, so the surviving controller
     * must get its own working one back instead of a facade over a closed storage.
     */
    public static void restore(ServerCooldowns previous) {
        INSTANCE = previous;
    }

    /** The one descriptor of this collection. */
    private static EntityDescriptor<String, ServerCooldownRow> descriptor(ParsedStorageConfig parsed, String backendName) {
        BackendDefinition backend = parsed.getBackend(backendName).orElseThrow(() ->
                new StorageConfigException("Account backend '" + backendName + "' is not declared/enabled!"));
        Codec<ServerCooldownRow> codec = BindingResolver.defaultCodec(backend, ServerCooldownRow.class);
        return EntityDescriptor
                .builder(String.class, ServerCooldownRow.class)
                .collection(COLLECTION)
                .keyExtractor(ServerCooldownRow::getIdentifier)
                .codec(codec)
                .build();   // @OptimisticLock is scanned here
    }

    /** The backend the rows live on (the shared one the account family uses). */
    public String getBackendName() {
        return backendName;
    }

    /** The cache/repository facade - handed to the cache-sync wiring alongside the other managers. */
    public CachingManager<String, ServerCooldownRow> getManager() {
        return manager;
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    /**
     * The handle over {@code identifier}'s network-wide state. Synchronous, which the warm collection
     * is what makes affordable: a cached identifier costs nothing to reach.
     *
     * <p>A cache miss is NOT answered as "no cooldown" - it reads through to the backend first. A cell
     * the cache-sync marked stale, because a peer has just written this very cooldown, misses exactly
     * like a never-seen identifier does, and answering "free" there would be the cross-server bypass
     * this route exists to prevent. So the read-through only ever costs a point read, and only for an
     * identifier no row exists for yet or one a peer just wrote.</p>
     *
     * <p>An identifier that genuinely has no row gets a blank one cached (no write): it becomes the one
     * live instance every later handle over that id shares, which is what lets them accumulate onto the
     * same state. It reaches the backend only once something mutates it.</p>
     */
    public NetworkCooldown resolve(String identifier) {
        ServerCooldownRow row = manager.peek(identifier)
                .orElseGet(() -> manager.resolve(identifier).join()
                        .orElseGet(() -> manager.seedIfAbsent(identifier, new ServerCooldownRow(identifier))));
        return new NetworkCooldown(identifier, row, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Persists {@code row} right away - the route's mutation hook. A server cooldown is a rare,
     * discrete event whose whole point is to be seen by the rest of the network, so it is not worth
     * holding for the next flush tick: waiting would leave every peer reading it as free until then.
     *
     * <p>The caller is a plain cooldown mutation with nowhere to report a failure to, so the write is
     * not awaited here; a failed one leaves the row dirty and {@link #flushDirty()} retries it.</p>
     */
    CompletableFuture<Void> store(ServerCooldownRow row) {
        //the handle may still hold the blank row resolve() cached: make sure the instance the flush
        //machinery finds and writes through is this very one
        manager.seedIfAbsent(row.getIdentifier(), row);
        row.markDirty();
        return track(persist(Collections.singletonList(row)));
    }

    /**
     * Awaits the writes still in flight, then re-persists whatever did not land (a failed write leaves
     * its row dirty). Driven by the periodic flush and by the shutdown/reload barrier - {@link #store}
     * has already fired everything that succeeded.
     *
     * <p>Awaiting is what makes this a real barrier: a write is fired outside the flush, and the row is
     * marked clean before it is handed to the backend, so a shutdown that only looked at the dirty set
     * would find nothing and close the backend from under a write still on its way.</p>
     */
    public CompletableFuture<Void> flushDirty() {
        return awaitPendingWrites().thenCompose(fired -> {
            List<ServerCooldownRow> dirty = new ArrayList<>();
            for (ServerCooldownRow row : manager.cachedValues()) {
                if (row.isDirty()) {
                    dirty.add(row);
                }
            }
            return dirty.isEmpty() ? CompletableFuture.<Void>completedFuture(null) : persist(dirty);
        });
    }

    private CompletableFuture<Void> persist(List<ServerCooldownRow> rows) {
        for (ServerCooldownRow row : rows) {
            row.markClean(); //cleared before persisting; a concurrent change re-sets it (at-least-once)
        }
        return flusher.persistBatch(manager, rows, FlushMode.BACKGROUND, WHAT, HOOKS, null);
    }

    private CompletableFuture<Void> awaitPendingWrites() {
        //BACKGROUND never completes exceptionally, so this only ever waits
        return CompletableFuture.allOf(pendingWrites.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> track(CompletableFuture<Void> write) {
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> pendingWrites.remove(write));
        return write;
    }

    /**
     * A conflicted row MERGES the winner in instead of adopting it wholesale: both sides are a real
     * state of the same cooldown, and only {@link CooldownEntry#latest} knows which one that cooldown
     * is actually in.
     */
    private static final ConflictHooks<String, ServerCooldownRow> HOOKS = new ConflictHooks<String, ServerCooldownRow>() {
        @Override public String storageKey(ServerCooldownRow live) { return live.getIdentifier(); }
        @Override public ReentrantLock lock(ServerCooldownRow live) { return live.getLock(); }
        @Override public void adoptStoredState(ServerCooldownRow live, ServerCooldownRow stored) {
            live.mergeStoredState(stored);
        }
        @Override public void adoptStoredLockVersion(ServerCooldownRow live, ServerCooldownRow stored) {
            live.adoptStoredLockVersion(stored);
        }
        @Override public void resetLockForRecreate(ServerCooldownRow live) { live.resetLockForRecreate(); }
        @Override public boolean mergesOnConflict() { return true; }
    };
}
