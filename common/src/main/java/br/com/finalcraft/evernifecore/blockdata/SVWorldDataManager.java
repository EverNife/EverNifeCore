package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkDataCodec;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
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
public final class SVWorldDataManager<O> {

    private final Class<O> valueType;
    private final CachingManager<String, WorldChunkData<O>> manager;
    private final List<BlockChangeListener<O>> listeners;
    private final Duration autoFlushPeriod;
    private final File legacyFolder;
    private final boolean preloadOnBuild;

    /** The plugin owning the storage, or {@code null} on a plugin-less open - only used for logging. */
    private final ECPluginData plugin;

    private SVWorldDataManager(BuilderImp<O> settings, CachingManager<String, WorldChunkData<O>> manager) {
        this.valueType = settings.valueType;
        this.manager = manager;
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
         * ({@code AUTO | ALWAYS | NEVER}), writing each default into the section when absent.
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
         * {@link #refs(RefParticipation)} selects, and checks the stored grid against the running one.
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
                    .policy(CachePolicy.fromAdminConfig(policyName, ttlSeconds))
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
            SVWorldDataManager<O> built = new SVWorldDataManager<>(this, manager);
            CompletableFuture<SVWorldDataManager<O>> opened = new CompletableFuture<>();
            built.checkGrid().whenComplete((ignored, failure) -> {
                if (failure == null) {
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

    // -----------------------------------------------------------------------------------------------------------------
    //  Maintenance
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The cache and repository behind the chunk entities - for statistics, or to wire something this facade
     * does not expose (cache-sync, a manual eviction). Persisting through it bypasses the flush's
     * snapshot-under-lock, so writes stay with the facade.
     */
    public CachingManager<String, WorldChunkData<O>> manager() {
        return manager;
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

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }
}
