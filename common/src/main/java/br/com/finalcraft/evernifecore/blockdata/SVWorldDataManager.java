package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkDataCodec;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheEntry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import br.com.finalcraft.everydatabase.query.Cursor;
import br.com.finalcraft.everydatabase.query.ScanRow;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-block data ({@code world -> chunk -> block -> O}) on any EveryDatabase backend, write-back: a block
 * write mutates the chunk entity living in the cache and the flush persists it later.
 *
 * <p>There is no second copy of the data. The {@link WorldChunkData} instance the cache holds IS the state,
 * so every read and write goes through the manager and takes that entity's monitor for the few instructions
 * it needs.
 *
 * <pre>{@code
 * STORAGE = ECStorage.openOrReload(getEcPluginData(), config.getConfigSection("storage"), STORAGE).join();
 * BLOCKS  = SVWorldDataManager.targeting(MyBlockInfo.class)
 *         .on(STORAGE, "my_blocks")
 *         .cache(config.getConfigSection("block-cache"))
 *         .onChange((world, pos, oldValue, newValue) -> { ... })
 *         .build().join();
 * }</pre>
 *
 * <p><b>Never retain a value and mutate it.</b> A block value handed out by {@link #getBlock} or
 * {@link #getChunk} is the live instance; changing it outside {@link #computeBlock} races the encode the
 * flush is free to start at any moment, and leaves the chunk clean, so the change is silently lost.
 *
 * <p>Every future completes on whichever thread the backend answered on - a storage callback thread, not the
 * server thread. Hop to your own scheduler before touching the game.
 *
 * @param <O> the block-value type, anything the backend's Jackson mapper can (de)serialize
 */
public final class SVWorldDataManager<O> implements AutoCloseable {

    /** How far a tick may drift from the configured period, so servers sharing a backend never flush in lockstep. */
    private static final long FLUSH_JITTER_MS = 60_000L;
    /** How long {@link #close()} waits for the final flush before handing the shutdown back. */
    private static final long CLOSE_FLUSH_TIMEOUT_SECONDS = 30L;
    /** How many failing chunk keys one log line names before it just counts the rest. */
    private static final int MAX_NAMED_FAILURES = 10;
    /** How many chunks a preload reads per page, so a huge collection never lands in one list. */
    private static final int PRELOAD_PAGE_SIZE = 256;

    private final Class<O> valueType;
    private final CachingManager<String, WorldChunkData<O>> manager;
    private final RefRegistry refRegistry;
    private final List<BlockChangeListener<O>> listeners;
    private final Duration autoFlushPeriod;
    private final File legacyFolder;
    private final boolean preloadOnBuild;

    /** The plugin owning the storage, or {@code null} on a plugin-less open - only used for logging. */
    private final ECPluginData plugin;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** The periodic flush, or {@code null} while auto-flush is off ({@link Duration#ZERO}). */
    private volatile ScheduledExecutorService flushTicker;
    /** The pass the last tick started, so a backend still answering the previous one gets no second pass. */
    private volatile CompletableFuture<FlushReport> tickPass;

    private SVWorldDataManager(BuilderImp<O> settings, RefRegistry refRegistry,
                               CachingManager<String, WorldChunkData<O>> manager) {
        this.valueType = settings.valueType;
        this.manager = manager;
        this.refRegistry = refRegistry;
        this.listeners = new CopyOnWriteArrayList<>(settings.listeners);
        this.autoFlushPeriod = settings.autoFlushPeriod;
        this.legacyFolder = settings.legacyFolder;
        this.preloadOnBuild = settings.resolvePreload();
        this.plugin = settings.ecStorage != null ? settings.ecStorage.plugin() : null;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Builder
    // -----------------------------------------------------------------------------------------------------------------

    /** Starts a manager for block values of type {@code valueType}. */
    public static <O> StepStorage<O> targeting(Class<O> valueType) {
        return new BuilderImp<>(valueType);
    }

    public interface StepStorage<O> {

        /**
         * The collection to open on the plugin's own storage handle - the production path. The backend, its
         * format and the {@code Ref} graph all come from the {@link ECStorage} the plugin opened, never from
         * the core's {@code storage.yml}.
         */
        Builder<O> on(ECStorage storage, String collection);

        /**
         * TEST-ONLY: a collection on a bare {@link Storage}. There is no plugin behind it, so entities are
         * encoded as compact JSON regardless of what the backend would have configured, and nothing takes
         * part in a plugin's {@code Ref} graph. Production opens through
         * {@link #on(ECStorage, String)}.
         */
        Builder<O> on(Storage storage, String collection);
    }

    public interface Builder<O> {

        /** The cache holding the live chunk entities; defaults to unbounded and never-expiring. */
        Builder<O> cache(CacheOptions options);

        /**
         * Reads the cache preset an admin controls: {@code policy} ({@code ALWAYS | TTL}),
         * {@code ttl-seconds}, {@code max-chunks} ({@code 0} = unbounded) and {@code preload}
         * ({@code AUTO | ALWAYS | NEVER}, where AUTO reads the whole collection in at build exactly when
         * the cache would end up holding it anyway), writing each default into the section when absent.
         */
        Builder<O> cache(ConfigSection adminSection);

        /** How often dirty chunks are persisted; defaults to 10 minutes, {@link Duration#ZERO} turns it off. */
        Builder<O> autoFlushEvery(Duration period);

        /** Whether this manager joins the plugin's {@code Ref} graph; defaults to {@link RefParticipation#SHARED}. */
        Builder<O> refs(RefParticipation participation);

        /** Adds a listener notified after every block change; may be called more than once. */
        Builder<O> onChange(BlockChangeListener<O> listener);

        /** The legacy region-YAML folder to import once ({@code <folder>/<world>/r.X.Z.yml}); optional. */
        Builder<O> importingLegacyFrom(File legacyFolder);

        /**
         * Opens the collection: resolves the codec, registers the manager in the {@code Ref} registry its
         * {@link #refs(RefParticipation)} selects, checks the stored grid against the running one, and reads
         * the stored chunks into the cache when the preset asked for a preload.
         *
         * <p>Fails with a {@link StorageConfigException} when the plugin already has a SHARED manager, and
         * when the collection was written with a different chunk size.
         */
        CompletableFuture<SVWorldDataManager<O>> build();
    }

    private static final class BuilderImp<O> implements StepStorage<O>, Builder<O> {

        private final Class<O> valueType;
        private ECStorage ecStorage;
        private Storage rawStorage;
        private String collection;
        private CacheOptions cacheOptions = CacheOptions.of(CachePolicy.always());
        private Duration autoFlushPeriod = Duration.ofMinutes(10);
        private RefParticipation participation = RefParticipation.SHARED;
        private final List<BlockChangeListener<O>> listeners = new CopyOnWriteArrayList<>();
        private File legacyFolder;

        /** {@code null} while the preset says AUTO, which only {@link #resolvePreload()} can answer. */
        private Boolean preloadOverride;

        private BuilderImp(Class<O> valueType) {
            this.valueType = Objects.requireNonNull(valueType, "valueType");
        }

        @Override
        public Builder<O> on(ECStorage storage, String collection) {
            this.ecStorage = Objects.requireNonNull(storage, "storage");
            this.rawStorage = storage.storage();
            this.collection = Objects.requireNonNull(collection, "collection");
            return this;
        }

        @Override
        public Builder<O> on(Storage storage, String collection) {
            this.ecStorage = null;
            this.rawStorage = Objects.requireNonNull(storage, "storage");
            this.collection = Objects.requireNonNull(collection, "collection");
            return this;
        }

        @Override
        public Builder<O> cache(CacheOptions options) {
            Objects.requireNonNull(options, "options");
            if (!options.policy().cacheable()) {
                throw new StorageConfigException("A policy that caches nothing cannot back a block store:"
                        + " a changed block lives in the cache until the flush persists it, so every write"
                        + " would be dropped on the spot. Bound the memory with CachePolicy.ttl(...) or"
                        + " CacheOptions.builder().maxSize(...) instead of CachePolicy.noCache().");
            }
            this.cacheOptions = options;
            return this;
        }

        @Override
        public Builder<O> cache(ConfigSection adminSection) {
            String policyName = adminSection.getOrSetValueIfAbsent("policy", "ALWAYS", "ALWAYS | TTL");
            if ("NOCACHE".equalsIgnoreCase(policyName)) {
                throw new StorageConfigException("NOCACHE is not usable here: this is a write-back store, so"
                        + " a changed block LIVES in the cache until the flush persists it and an uncached"
                        + " store would have nowhere to hold it. Set policy to TTL with a short ttl-seconds"
                        + " to keep the memory low, or to ALWAYS to keep every visited chunk resident.");
            }
            Integer ttlSeconds = adminSection.getOrSetValueIfAbsent("ttl-seconds", 600, "used when policy=TTL");
            int maxChunks = adminSection.getOrSetValueIfAbsent("max-chunks", 0, "0 = unbounded (LRU above 0)");
            String preload = adminSection.getOrSetValueIfAbsent("preload", "AUTO", "AUTO | ALWAYS | NEVER");

            this.cacheOptions = CacheOptions.builder()
                    .policy(readPolicy(policyName, ttlSeconds, adminSection))
                    .maxSize(maxChunks)
                    .build();
            this.preloadOverride = readPreload(preload, adminSection);
            return this;
        }

        @Override
        public Builder<O> autoFlushEvery(Duration period) {
            this.autoFlushPeriod = Objects.requireNonNull(period, "period (use Duration.ZERO to disable)");
            return this;
        }

        @Override
        public Builder<O> refs(RefParticipation participation) {
            this.participation = Objects.requireNonNull(participation, "participation");
            return this;
        }

        @Override
        public Builder<O> onChange(BlockChangeListener<O> listener) {
            this.listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        @Override
        public Builder<O> importingLegacyFrom(File legacyFolder) {
            this.legacyFolder = legacyFolder;
            return this;
        }

        @Override
        public CompletableFuture<SVWorldDataManager<O>> build() {
            final RefRegistry registry = refRegistry();
            //asking the registry first, instead of translating the IllegalStateException its register()
            //throws: a closed or detached ECStorage answers with one too, and it says something else
            if (registry.isRegistered(WorldChunkData.class)) {
                return failed(new StorageConfigException("This plugin already has a SVWorldDataManager"
                        + " participating in its Ref graph, and a Ref graph answers one resolver per entity"
                        + " type - which every manager shares. Build this one with"
                        + " .refs(RefParticipation.ISOLATED) so nothing outside resolves it, or close the"
                        + " other manager first."));
            }
            final CachingManager<String, WorldChunkData<O>> manager;
            try {
                manager = registry.manager(descriptor(), rawStorage, cacheOptions);
            } catch (RuntimeException openFailure) {
                return failed(openFailure);
            }
            SVWorldDataManager<O> built = new SVWorldDataManager<>(this, registry, manager);
            CompletableFuture<SVWorldDataManager<O>> opened = new CompletableFuture<>();
            //preload only once the grid matched: warming the cache from a collection whose keys name chunks
            //of another size would fill it with entities no coordinate of this runtime resolves to
            built.checkGrid().thenCompose(checked -> built.preloadChunks()).whenComplete((ignored, failure) -> {
                if (failure == null) {
                    //only now: a manager that never opened must not leave a thread ticking over a
                    //collection nobody holds
                    built.startAutoFlush();
                    opened.complete(built);
                    return;
                }
                //a half-built manager must not keep the Ref registration: it would refuse every later
                //attempt to build the manager that IS meant to serve this type
                registry.unregister(WorldChunkData.class);
                opened.completeExceptionally(unwrap(failure));
            });
            return opened;
        }

        /** The registry the manager registers in: the plugin's own, or a child of it that nothing can see. */
        private RefRegistry refRegistry() {
            RefRegistry pluginGraph = ecStorage != null ? ecStorage.refRegistry() : new RefRegistry();
            return participation == RefParticipation.SHARED ? pluginGraph : new RefRegistry(pluginGraph);
        }

        @SuppressWarnings("unchecked")
        private EntityDescriptor<String, WorldChunkData<O>> descriptor() {
            Class<WorldChunkData<O>> entityType = (Class<WorldChunkData<O>>) (Class<?>) WorldChunkData.class;
            return EntityDescriptor.builder(String.class, entityType)
                    .collection(collection)
                    .keyExtractor(WorldChunkData::getChunkKey)
                    .codec(codec())
                    .build();
        }

        /**
         * The backend's own codec with the block values bound to {@code O}: an entity lands in the format the
         * admin configured and its values carry the platform types and {@code Ref}s of that backend.
         */
        private Codec<WorldChunkData<O>> codec() {
            if (ecStorage == null) {
                return WorldChunkDataCodec.jsonFallback(valueType);
            }
            return WorldChunkDataCodec.composing(valueType, ecStorage.defaultCodec(WorldChunkData.class));
        }

        /**
         * The freshness the preset asks for. NOCACHE is already out by the time this runs, so the generic
         * failure of {@code fromAdminConfig} - which still offers it - would send an admin straight back to
         * the value this store refuses.
         */
        private CachePolicy readPolicy(String policyName, Integer ttlSeconds, ConfigSection section) {
            try {
                return CachePolicy.fromAdminConfig(policyName, ttlSeconds);
            } catch (IllegalArgumentException unknownPolicy) {
                throw new StorageConfigException("'" + policyName + "' at '" + section.getPath() + ".policy'"
                        + " is not a cache policy. Use ALWAYS to keep every chunk the server touched"
                        + " resident, or TTL to let a chunk nobody touched for ttl-seconds go.",
                        unknownPolicy);
            }
        }

        private Boolean readPreload(String preload, ConfigSection section) {
            if ("AUTO".equalsIgnoreCase(preload)) {
                return null;
            }
            if ("ALWAYS".equalsIgnoreCase(preload)) {
                return Boolean.TRUE;
            }
            if ("NEVER".equalsIgnoreCase(preload)) {
                return Boolean.FALSE;
            }
            throw new StorageConfigException("'" + preload + "' at '" + section.getPath() + ".preload' is not a"
                    + " preload mode. Use AUTO to preload only when the whole collection would fit (policy"
                    + " ALWAYS and max-chunks 0), ALWAYS to load it at boot regardless, or NEVER to load each"
                    + " chunk on first touch.");
        }

        /** AUTO preloads exactly when the cache would hold the whole collection anyway. */
        private boolean resolvePreload() {
            if (preloadOverride != null) {
                return preloadOverride;
            }
            return cacheOptions.policy() == CachePolicy.always() && cacheOptions.maxSize() == CacheOptions.UNBOUNDED;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The value stored at a block, or {@code null} when there is none. Loads the chunk when it is not cached;
     * a chunk with no stored blocks is never created by a read.
     */
    public CompletableFuture<O> getBlock(String world, BlockPos pos) {
        String blockKey = pos.serialize();
        return chunkIfPresent(world, pos.getChunkPos()).thenApply(chunk -> {
            if (chunk == null) {
                return null;
            }
            synchronized (chunk) {
                return chunk.getValue(blockKey);
            }
        });
    }

    /** {@link #getBlock(String, BlockPos)} for a position that already carries its world. */
    public CompletableFuture<O> getBlock(WorldBlockPos pos) {
        return getBlock(pos.getWorldName(), pos.getBlockPos());
    }

    /**
     * The value stored at a block if its chunk is already cached, {@code null} otherwise - no I/O, so a
     * {@code null} means "absent OR not loaded". For the answer that distinguishes the two, use
     * {@link #getBlock}.
     */
    public @Nullable O peekBlock(String world, BlockPos pos) {
        WorldChunkData<O> chunk = cachedChunk(world, pos.getChunkPos());
        if (chunk == null) {
            return null;
        }
        synchronized (chunk) {
            return chunk.getValue(pos.serialize());
        }
    }

    /** {@link #peekBlock(String, BlockPos)} for a position that already carries its world. */
    public @Nullable O peekBlock(WorldBlockPos pos) {
        return peekBlock(pos.getWorldName(), pos.getBlockPos());
    }

    /**
     * Every block stored in one chunk, as an immutable snapshot taken while the chunk was locked - later
     * writes do not show up in it. Empty when the chunk holds nothing.
     */
    public CompletableFuture<Map<BlockPos, O>> getChunk(String world, ChunkPos chunkPos) {
        return chunkIfPresent(world, chunkPos).thenApply(chunk -> {
            if (chunk == null) {
                return Collections.<BlockPos, O>emptyMap();
            }
            Map<String, O> snapshot;
            synchronized (chunk) {
                snapshot = chunk.snapshotValues();
            }
            Map<BlockPos, O> blocks = new LinkedHashMap<>(snapshot.size());
            for (Map.Entry<String, O> entry : snapshot.entrySet()) {
                blocks.put(BlockPos.deserialize(entry.getKey()), entry.getValue());
            }
            return Collections.unmodifiableMap(blocks);
        });
    }

    /**
     * Every block stored inside the cube of side {@code 2 * radius + 1} centered on {@code center}: a radius
     * of 0 asks for that single block, a radius of 3 for the 7x7x7 around it. The cube reaches as far up and
     * down as it does sideways.
     *
     * <p>The chunks the cube covers are read together - the cached ones answer from memory and every other
     * one comes back in a single call, so the backend is asked once no matter how wide the radius is. A chunk
     * holding nothing contributes nothing and is not created.
     *
     * <p>Each chunk is snapshotted while locked, so the values are the ones it held at that moment. The order
     * of the list carries no meaning.
     *
     * @param radius how far the cube reaches from the center, in blocks; never negative
     */
    public CompletableFuture<List<BlockRecord<O>>> getRange(String world, BlockPos center, int radius) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(center, "center");
        if (radius < 0) {
            throw new IllegalArgumentException("A range radius cannot be negative (got " + radius + "). Pass 0"
                    + " to ask for the single block at the center, or N for the (2N+1)-blocks-wide cube"
                    + " around it.");
        }
        //long bounds: a center near the end of the int range plus a wide radius would wrap around and answer
        //with the blocks of the opposite corner of the world instead of the ones asked for
        final long lowX = (long) center.getX() - radius;
        final long highX = (long) center.getX() + radius;
        final long lowY = (long) center.getY() - radius;
        final long highY = (long) center.getY() + radius;
        final long lowZ = (long) center.getZ() - radius;
        final long highZ = (long) center.getZ() + radius;

        ChunkPos lowChunk = ChunkPos.fromBlock(blockBound(lowX), blockBound(lowZ));
        ChunkPos highChunk = ChunkPos.fromBlock(blockBound(highX), blockBound(highZ));
        List<String> chunkKeys = new ArrayList<>();
        for (int chunkX = lowChunk.getX(); chunkX <= highChunk.getX(); chunkX++) {
            for (int chunkZ = lowChunk.getZ(); chunkZ <= highChunk.getZ(); chunkZ++) {
                chunkKeys.add(WorldChunkData.keyOf(world, ChunkPos.of(chunkX, chunkZ)));
            }
        }

        return manager.getAll(chunkKeys).thenApply(chunks -> {
            List<BlockRecord<O>> found = new ArrayList<>();
            for (WorldChunkData<O> chunk : chunks) {
                //no chunk key ever names the grid sentinel, but a world inside the reserved key space would
                //produce one - and the sentinel holds store metadata, never blocks a query may answer with
                if (WorldChunkData.isMetaKey(chunk.getChunkKey())) {
                    continue;
                }
                Map<String, O> snapshot;
                synchronized (chunk) {
                    snapshot = chunk.snapshotValues();
                }
                for (Map.Entry<String, O> block : snapshot.entrySet()) {
                    BlockPos pos = BlockPos.deserialize(block.getKey());
                    if (pos.getX() >= lowX && pos.getX() <= highX
                            && pos.getY() >= lowY && pos.getY() <= highY
                            && pos.getZ() >= lowZ && pos.getZ() <= highZ) {
                        found.add(BlockRecord.of(world, pos, block.getValue()));
                    }
                }
            }
            return Collections.unmodifiableList(found);
        });
    }

    /** {@link #getRange(String, BlockPos, int)} around a center that already carries its world. */
    public CompletableFuture<List<BlockRecord<O>>> getRange(WorldBlockPos center, int radius) {
        return getRange(center.getWorldName(), center.getBlockPos(), radius);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Writing
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Stores a value at a block, replacing whatever was there. The write lands in the cached chunk and is
     * persisted by the next flush.
     */
    public CompletableFuture<Void> setBlock(String world, BlockPos pos, O value) {
        Objects.requireNonNull(value, "value (use removeBlock to delete a block)");
        String blockKey = pos.serialize();
        return chunkOf(world, pos.getChunkPos()).thenAccept(chunk -> {
            O previous;
            synchronized (chunk) {
                previous = chunk.putValue(blockKey, value);
            }
            notifyChange(world, pos, previous, value);
        });
    }

    /** {@link #setBlock(String, BlockPos, Object)} at a position that already carries its world. */
    public CompletableFuture<Void> setBlock(WorldBlockPos pos, O value) {
        return setBlock(pos.getWorldName(), pos.getBlockPos(), value);
    }

    /** Drops the value stored at a block, if any. An emptied chunk is deleted by the flush, not here. */
    public CompletableFuture<Void> removeBlock(String world, BlockPos pos) {
        String blockKey = pos.serialize();
        return chunkIfPresent(world, pos.getChunkPos()).thenAccept(chunk -> {
            if (chunk == null) {
                return;
            }
            O removed;
            synchronized (chunk) {
                removed = chunk.removeValue(blockKey);
            }
            if (removed != null) {
                notifyChange(world, pos, removed, null);
            }
        });
    }

    /** {@link #removeBlock(String, BlockPos)} at a position that already carries its world. */
    public CompletableFuture<Void> removeBlock(WorldBlockPos pos) {
        return removeBlock(pos.getWorldName(), pos.getBlockPos());
    }

    /**
     * Applies {@code mutator} to the value stored at a block while holding the chunk, and stores what it
     * returns ({@code null} removes the block). The mutator sees {@code null} when the block is empty and may
     * mutate the value it was given in place.
     *
     * <p>The only safe way to change a mutable value: read-modify-write outside this method races both a
     * concurrent writer and the flush's encode.
     *
     * @return the value now stored at the block, or {@code null} when the mutator removed it
     */
    public CompletableFuture<O> computeBlock(String world, BlockPos pos, UnaryOperator<O> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        String blockKey = pos.serialize();
        return chunkOf(world, pos.getChunkPos()).thenApply(chunk -> {
            O previous;
            O updated;
            synchronized (chunk) {
                previous = chunk.getValue(blockKey);
                updated = mutator.apply(previous);
                if (updated == null) {
                    chunk.removeValue(blockKey);
                } else {
                    chunk.putValue(blockKey, updated);
                }
            }
            if (previous != null || updated != null) {
                notifyChange(world, pos, previous, updated);
            }
            return updated;
        });
    }

    /** {@link #computeBlock(String, BlockPos, UnaryOperator)} at a position that already carries its world. */
    public CompletableFuture<O> computeBlock(WorldBlockPos pos, UnaryOperator<O> mutator) {
        return computeBlock(pos.getWorldName(), pos.getBlockPos(), mutator);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Maintenance
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Persists every chunk changed since the last flush and deletes the entity of every chunk left with no
     * blocks. Changed chunks go out as one batched write; each chunk is snapshotted while its monitor is
     * held, so what gets encoded is never a map a writer is still mutating.
     *
     * <p>A write that fails leaves its chunks dirty, so the next flush retries them - nothing is dropped on
     * a backend outage. The report is returned and logged either way: at debug level when the flush was
     * clean, and as a failure naming the chunks when it was not.
     *
     * <p>Runs on its own every {@code autoFlushEvery} period; call it directly only when something must be
     * on disk now (a manual save command, a backup).
     */
    public CompletableFuture<FlushReport> flush() {
        List<WorldChunkData<O>> changed = new ArrayList<>();
        List<WorldChunkData<O>> snapshots = new ArrayList<>();
        List<WorldChunkData<O>> emptied = new ArrayList<>();
        for (WorldChunkData<O> chunk : manager.cachedValues()) {
            if (!chunk.isDirty()) {
                continue;
            }
            //the grid sentinel shares this collection and holds no blocks: taken for a chunk it would look
            //empty, and the delete would drop the very entry the next boot checks its grid against
            if (WorldChunkData.isMetaKey(chunk.getChunkKey())) {
                continue;
            }
            synchronized (chunk) {
                if (!chunk.isDirty()) {
                    continue;
                }
                chunk.markClean();
                if (chunk.isEmpty()) {
                    emptied.add(chunk);
                } else {
                    changed.add(chunk);
                    snapshots.add(chunk.copyForSave());
                }
            }
        }

        Map<String, Throwable> failures = Collections.synchronizedMap(new LinkedHashMap<String, Throwable>());
        AtomicInteger savedChunks = new AtomicInteger();
        AtomicInteger deletedChunks = new AtomicInteger();
        List<CompletableFuture<?>> writes = new ArrayList<>(emptied.size() + 1);
        if (!snapshots.isEmpty()) {
            writes.add(saveChanged(changed, snapshots, failures, savedChunks));
        }
        for (WorldChunkData<O> chunk : emptied) {
            writes.add(deleteEmptied(chunk, failures, deletedChunks));
        }
        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0])).thenApply(done -> {
            FlushReport report = new FlushReport(savedChunks.get(), deletedChunks.get(), failures);
            logReport(report);
            return report;
        });
    }

    /**
     * Writes the snapshots of every changed chunk in one call. A batch answers with a single failure for the
     * whole list, so all of them go back to dirty: re-saving one that did land costs a write, losing one that
     * did not would be silent.
     */
    private CompletableFuture<Void> saveChanged(List<WorldChunkData<O>> changed, List<WorldChunkData<O>> snapshots,
                                                Map<String, Throwable> failures, AtomicInteger savedChunks) {
        return manager.repository().saveAll(snapshots).handle((done, failure) -> {
            if (failure == null) {
                savedChunks.addAndGet(snapshots.size());
                return null;
            }
            Throwable cause = unwrap(failure);
            for (WorldChunkData<O> chunk : changed) {
                chunk.markDirty();
                failures.put(chunk.getChunkKey(), cause);
            }
            return null;
        });
    }

    /** Drops the backing entity of a chunk that lost its last block, cache entry included. */
    private CompletableFuture<Void> deleteEmptied(WorldChunkData<O> emptied, Map<String, Throwable> failures,
                                                  AtomicInteger deletedChunks) {
        String chunkKey = emptied.getChunkKey();
        return manager.deleteAndEvict(chunkKey).handle((existed, failure) -> {
            if (failure != null) {
                emptied.markDirty();
                failures.put(chunkKey, unwrap(failure));
                return null;
            }
            deletedChunks.incrementAndGet();
            if (emptied.isDirty()) {
                //a writer refilled the chunk while the delete was in flight, and the eviction just dropped
                //the instance it is holding - put that same instance back so the next flush persists it
                manager.seedIfAbsent(chunkKey, emptied);
            }
            return null;
        });
    }

    /**
     * The cache and repository behind the chunk entities - for statistics, or to wire something this facade
     * does not expose (cache-sync, a manual eviction). Persisting through it bypasses the flush's
     * snapshot-under-lock, so writes stay with the facade.
     */
    public CachingManager<String, WorldChunkData<O>> manager() {
        return manager;
    }

    /**
     * Stops the periodic flush and persists what is still dirty, waiting up to 30 seconds for the backend: a
     * storage that hung delays a shutdown, it never owns it. Chunks that miss that window stay dirty in
     * memory and go with the process.
     *
     * <p>Does NOT close the {@link Storage} behind the collection - that belongs to the plugin's
     * {@link ECStorage} and usually serves other collections too. The {@code Ref} registration IS released,
     * so a reload can build this manager again.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService ticker = flushTicker;
        if (ticker != null) {
            ticker.shutdownNow();
        }
        try {
            flush().get(CLOSE_FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException hungBackend) {
            logSevere("The final flush of the '" + manager.collection() + "' block store did not answer within "
                    + CLOSE_FLUSH_TIMEOUT_SECONDS + "s, so the shutdown goes on without it. The chunks it"
                    + " could not write are still dirty in memory and are lost with the process - check"
                    + " whether the backend is reachable before starting up again.", hungBackend);
        } catch (ExecutionException flushFailure) {
            logSevere("The final flush of the '" + manager.collection() + "' block store failed. The chunks it"
                    + " could not write are still dirty in memory and are lost with the process.",
                    unwrap(flushFailure));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logSevere("The final flush of the '" + manager.collection() + "' block store was interrupted, so"
                    + " what was still dirty was not written.", interrupted);
        } finally {
            refRegistry.unregister(WorldChunkData.class);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Periodic flush
    // -----------------------------------------------------------------------------------------------------------------

    /** Arms the periodic flush on a daemon thread of its own; a zero period leaves flushing to the caller. */
    private void startAutoFlush() {
        if (autoFlushPeriod.isZero() || autoFlushPeriod.isNegative()) {
            return;
        }
        final String collection = manager.collection();
        this.flushTicker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ec-blockdata-flush-" + collection);
            thread.setDaemon(true);
            return thread;
        });
        scheduleNextTick();
    }

    private void scheduleNextTick() {
        ScheduledExecutorService ticker = flushTicker;
        if (ticker == null || closed.get()) {
            return;
        }
        long period = autoFlushPeriod.toMillis();
        //jitter: several servers sharing one backend must not hit it on the same beat every cycle
        long jitter = Math.min(FLUSH_JITTER_MS, period / 2);
        long delay = period - jitter + ThreadLocalRandom.current().nextLong(2 * jitter + 1);
        try {
            ticker.schedule(this::runTick, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stoppedMeanwhile) {
            //close() raced the reschedule - the ticker is gone and the final flush is already running
        }
    }

    private void runTick() {
        try {
            CompletableFuture<FlushReport> previous = tickPass;
            if (previous != null && !previous.isDone()) {
                //the backend is still answering the last pass; a second one would only queue behind it
                return;
            }
            //purge after the flush, not before: an expired chunk is exempt from purging while dirty, so it
            //only becomes collectable once its write has gone out
            tickPass = flush().whenComplete((report, failure) -> manager.purgeExpired());
        } catch (Throwable tickFailure) {
            logSevere("The '" + manager.collection() + "' block store could not start its periodic flush - the"
                    + " next tick tries again.", tickFailure);
        } finally {
            scheduleNextTick();
        }
    }

    /** The block-value type this manager was built for. */
    public Class<O> getValueType() {
        return valueType;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Internals
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The live entity of a chunk, created empty when neither the cache nor the backend has one. The single
     * entry point for a write: everything the manager mutates comes from here, so two writers on the same
     * chunk always meet on the same instance.
     */
    private CompletableFuture<WorldChunkData<O>> chunkOf(String world, ChunkPos chunkPos) {
        return manager.getOrCompute(keyOf(world, chunkPos), WorldChunkData::new);
    }

    /** As {@link #chunkOf}, but completing with {@code null} instead of creating an entity for a read. */
    private CompletableFuture<WorldChunkData<O>> chunkIfPresent(String world, ChunkPos chunkPos) {
        return manager.resolveCell(keyOf(world, chunkPos), manager.defaultPolicy())
                .thenApply(cell -> cell != null ? cell.getValue() : null);
    }

    /** The live entity of a chunk if the cache can serve it, {@code null} otherwise. Never reads the backend. */
    private WorldChunkData<O> cachedChunk(String world, ChunkPos chunkPos) {
        CacheEntry<WorldChunkData<O>> cell = manager.peekCell(keyOf(world, chunkPos), manager.defaultPolicy());
        return cell != null ? cell.getValue() : null;
    }

    /** A cube bound as a block coordinate, saturated: no block of any world lies past the int range. */
    private static int blockBound(long coordinate) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, coordinate));
    }

    private String keyOf(String world, ChunkPos chunkPos) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(chunkPos, "chunkPos");
        return WorldChunkData.keyOf(world, chunkPos);
    }

    /**
     * Announces one change to every listener, outside the chunk's monitor - a replace is a single call
     * carrying both sides. A listener that throws is logged and skipped: the write already happened, and
     * failing it afterwards would report a loss that did not occur.
     */
    private void notifyChange(String world, BlockPos pos, @Nullable O oldValue, @Nullable O newValue) {
        for (BlockChangeListener<O> listener : listeners) {
            try {
                listener.onBlockChange(world, pos, oldValue, newValue);
            } catch (Throwable listenerFailure) {
                logSevere("A block-change listener of the '" + manager.collection() + "' store failed for "
                        + world + " " + pos + " - the change itself was applied.", listenerFailure);
            }
        }
    }

    /**
     * Reads every stored chunk into the cache before the manager is handed out, so the first reads answer
     * from memory instead of the backend. Does nothing unless the preset asked for it.
     *
     * <p>A chunk whose stored payload does not decode is named in the log and left behind: naming it is the
     * whole reason the load walks a scan, where an ordinary read would just come back shorter and say
     * nothing. A scan that fails outright is logged the same way and the build goes on - a chunk that was
     * not cached is read on first touch, so the store works either way.
     */
    private CompletableFuture<Void> preloadChunks() {
        if (!preloadOnBuild) {
            return CompletableFuture.completedFuture(null);
        }
        AtomicInteger cachedChunks = new AtomicInteger();
        AtomicInteger unreadableChunks = new AtomicInteger();
        return preloadPage(Cursor.scan(), cachedChunks, unreadableChunks).handle((done, failure) -> {
            if (failure != null) {
                logSevere("The '" + manager.collection() + "' block store could not finish reading itself"
                        + " into memory. The " + cachedChunks.get() + " chunk(s) that made it in are cached"
                        + " and the rest are read on first touch, so nothing is lost - but a backend that"
                        + " cannot be scanned will fail the next flush too.", unwrap(failure));
                return null;
            }
            logDebug("Preloaded the '" + manager.collection() + "' block store: " + cachedChunks.get()
                    + " chunk(s) cached, " + unreadableChunks.get() + " unreadable.");
            return null;
        });
    }

    /** One page of the scan: caches what decoded, names what did not, then walks into the next page. */
    private CompletableFuture<Void> preloadPage(Cursor cursor, AtomicInteger cachedChunks,
                                                AtomicInteger unreadableChunks) {
        return manager.repository().scanAll(cursor, PRELOAD_PAGE_SIZE).thenCompose(page -> {
            for (ScanRow<WorldChunkData<O>> row : page.content()) {
                if (row.isFailed()) {
                    unreadableChunks.incrementAndGet();
                    logSevere("The '" + manager.collection() + "' block store cannot decode what is stored"
                            + " under '" + row.key() + "', so the blocks it holds are absent from this boot"
                            + " while every other chunk loaded. Repair that entry or delete it - a write to"
                            + " the same chunk overwrites it either way.", row.error());
                    continue;
                }
                //the grid sentinel shares this collection but is not a chunk: no coordinate resolves to it,
                //and everything walking the cached chunks would have to dodge a key that does not parse
                if (WorldChunkData.isMetaKey(row.key())) {
                    continue;
                }
                //seed, never install: a chunk a writer already touched is the live instance carrying blocks
                //no flush has persisted yet, and the copy this scan decoded does not have them
                manager.seedIfAbsent(row.key(), row.value());
                cachedChunks.incrementAndGet();
            }
            Optional<Cursor> next = page.nextCursor();
            return next.isPresent()
                    ? preloadPage(next.get(), cachedChunks, unreadableChunks)
                    : CompletableFuture.<Void>completedFuture(null);
        });
    }

    /**
     * Checks the chunk size the collection was written with against the running one, writing the sentinel
     * when the collection has none yet. The sentinel lives on the reserved {@link WorldChunkData#META_KEY}
     * of this same collection and is read straight from the repository, so it never enters the cache the
     * chunks share.
     */
    private CompletableFuture<Void> checkGrid() {
        int runtimeGrid = RegionGridOptions.getCurrent().getChunkSize();
        Repository<String, WorldChunkData<O>> repository = manager.repository();
        return repository.find(WorldChunkData.META_KEY).thenCompose(found -> {
            Integer writtenGrid = found.map(WorldChunkData::getGridChunkSize).orElse(null);
            if (writtenGrid == null) {
                return repository.save(WorldChunkData.<O>metaSentinel(runtimeGrid));
            }
            if (writtenGrid.intValue() != runtimeGrid) {
                throw new StorageConfigException("The '" + manager.collection() + "' block store was written"
                        + " with chunks of " + writtenGrid + " blocks, but this server's grid is "
                        + runtimeGrid + ". Every stored key names a chunk of the old size, so reading them"
                        + " under the new one would point at the wrong blocks. Put the grid back to "
                        + writtenGrid + " (RegionGridOptions), or migrate the collection to the new grid and"
                        + " drop its '" + WorldChunkData.META_KEY + "' entry so the new size is recorded.");
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Announces what a flush did. A clean one is routine and only shows with debug on; one with failures is
     * always loud and names the chunks, because those are the writes an admin has to know did not land.
     */
    private void logReport(FlushReport report) {
        Map<String, Throwable> failures = report.getFailures();
        if (failures.isEmpty()) {
            logDebug("Flushed the '" + manager.collection() + "' block store: " + report.getSavedChunks()
                    + " chunk(s) written, " + report.getDeletedChunks() + " emptied chunk(s) deleted.");
            return;
        }
        logSevere("The '" + manager.collection() + "' block store could not persist " + failures.size()
                + " chunk(s): " + namesOf(failures.keySet()) + ". They are dirty again and the next flush"
                + " retries them, so nothing is lost while the backend answers again.",
                failures.values().iterator().next());
    }

    /** The first few keys of a set, spelled out, with the rest counted - a log line, not a dump. */
    private static String namesOf(Collection<String> keys) {
        StringBuilder names = new StringBuilder();
        int named = 0;
        for (String key : keys) {
            if (named == MAX_NAMED_FAILURES) {
                names.append(" and ").append(keys.size() - named).append(" more");
                break;
            }
            names.append(named == 0 ? "" : ", ").append(key);
            named++;
        }
        return names.toString();
    }

    private void logDebug(String message) {
        if (plugin != null) {
            plugin.getLog().debug(message);
            return;
        }
        try {
            EverNifeCore.getLog().debug(message);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData/log configured): falls back to JUL
            Logger.getLogger("EverNifeCore").log(Level.FINE, message);
        }
    }

    private void logSevere(String message, Throwable cause) {
        if (plugin != null) {
            plugin.getLog().severe(message, cause);
            return;
        }
        try {
            EverNifeCore.getLog().severe(message, cause);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData/log configured): falls back to JUL
            Logger.getLogger("EverNifeCore").log(Level.SEVERE, message, cause);
        }
    }

    private static <T> CompletableFuture<T> failed(Throwable cause) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    /** The failure itself, past the wrapper a future puts around it. */
    private static Throwable unwrap(Throwable failure) {
        boolean wrapper = failure instanceof CompletionException || failure instanceof ExecutionException;
        return wrapper && failure.getCause() != null ? failure.getCause() : failure;
    }

    /**
     * What one {@link #flush()} did: the chunks it wrote, the emptied ones it deleted, and the chunks whose
     * write failed mapped to why. A failed chunk is dirty again, so it is retried by the next flush.
     */
    public static final class FlushReport {

        private final int savedChunks;
        private final int deletedChunks;
        private final Map<String, Throwable> failures;

        private FlushReport(int savedChunks, int deletedChunks, Map<String, Throwable> failures) {
            this.savedChunks = savedChunks;
            this.deletedChunks = deletedChunks;
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
        }

        /** How many changed chunks were written. */
        public int getSavedChunks() {
            return savedChunks;
        }

        /** How many chunks had lost their last block and had their entity deleted. */
        public int getDeletedChunks() {
            return deletedChunks;
        }

        /** The chunk keys whose write failed, each mapped to the failure; empty on a clean flush. */
        public Map<String, Throwable> getFailures() {
            return failures;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }

        @Override
        public String toString() {
            return "FlushReport{saved=" + savedChunks + ", deleted=" + deletedChunks
                    + ", failed=" + failures.size() + "}";
        }
    }
}
