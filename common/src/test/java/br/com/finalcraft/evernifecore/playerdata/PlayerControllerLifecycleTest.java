package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.HealthStatus;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.WriteMode;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.query.Cursor;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.query.QueryOptions;
import br.com.finalcraft.everydatabase.query.ScanRow;
import br.com.finalcraft.everydatabase.query.Slice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache lifecycle behaviour: the per-section lifecycle (lazy vs online vs resident, the idle release
 * and the maxCached bound), durable flush-on-quit with a storage-down retry, and {@code setPlayer} no
 * longer dirtying the base.
 * Runs on H2 mem - no Docker.
 */
@ECoreTest
class PlayerControllerLifecycleTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        FailableStorage.FAIL_WRITES.set(false);
    }

    public static class ResidentSection extends PDSection {
        public long value;
    }

    public static class OnlineSection extends PDSection {
        public long value;
    }

    public static class BoundedSection extends PDSection {
        public long value;
    }

    public static class LazySection extends PDSection {
        public long value;
    }


    // ------------------------------------------------------------------
    // workingSet evicts after quit + grace; resident (default) stays cached
    // ------------------------------------------------------------------

    @Test
    void workingSetEvictsAfterQuitGrace_residentStaysCached() throws Exception {
        PlayerController.initialize(Storages.h2("f_ws").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, ResidentSection.class, "resident")
                .lifecycle(SectionLifecycle.RESIDENT).build());
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, OnlineSection.class, "online")
                .lifecycle(SectionLifecycle.ONLINE).idleGrace(Duration.ofMillis(150)).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "WS").join();
        PlayerData playerData = PlayerController.getLoaded(uuid);

        //load (seed) both sections into cache
        playerData.getPDSection(ResidentSection.class).join();
        playerData.getPDSection(OnlineSection.class).join();
        assertNotNull(PlayerController.getLoadedSection(uuid, ResidentSection.class));
        assertNotNull(PlayerController.getLoadedSection(uuid, OnlineSection.class));

        //quit: the player is offline (no player attached), so the grace timer will evict the working set
        PlayerController.handlePlayerQuit(uuid);

        //resident stays cached immediately after quit
        assertNotNull(PlayerController.getLoadedSection(uuid, ResidentSection.class),
                "a resident section must NOT be evicted on quit");

        //after the grace, the working-set cell is gone but the resident one is not
        awaitUntil(Duration.ofSeconds(3), () ->
                PlayerController.getLoadedSection(uuid, OnlineSection.class) == null);
        assertNull(PlayerController.getLoadedSection(uuid, OnlineSection.class),
                "a working-set section must be evicted a grace after quit");
        assertNotNull(PlayerController.getLoadedSection(uuid, ResidentSection.class),
                "the resident section is still cached after the working-set eviction");
    }

    // ------------------------------------------------------------------
    // flush-on-quit persists a player's dirty state
    // ------------------------------------------------------------------

    @Test
    void quitFlushesDirtyState() throws Exception {
        PlayerController.initialize(Storages.h2("f_quitflush").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, ResidentSection.class, "resident").build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Quitter").join();
        ResidentSection section = PlayerController.getLoaded(uuid).getPDSection(ResidentSection.class).join();
        section.value = 777L;
        section.markDirty();

        //quit runs the flush asynchronously - wait for the row to appear
        PlayerController.handlePlayerQuit(uuid);
        awaitUntil(Duration.ofSeconds(3), () ->
                PlayerController.get().getBinding(ResidentSection.class).getRepository().exists(uuid).join());

        assertTrue(PlayerController.get().getBinding(ResidentSection.class).getRepository().exists(uuid).join(),
                "quit must have flushed the dirty section to the backend");
        assertEquals(777L, PlayerController.get().getBinding(ResidentSection.class)
                .getRepository().find(uuid).join().orElseThrow().value);
    }

    // ------------------------------------------------------------------
    // storage-down quit-flush is enqueued, then re-flushed when storage returns
    // ------------------------------------------------------------------

    @Test
    void quitFlushRetriesWhenStorageReturns() throws Exception {
        PlayerController.initialize(Storages.h2("f_retry").writeTo(tempDir));
        //wrap the backend's Storage so writes can be made to fail on demand (register AFTER bootstrap,
        //BEFORE the section binds, so the section manager resolves against the failing wrapper)
        wrapBackendWithFailable("test_h2");
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, ResidentSection.class, "resident").build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Flaky").join();
        ResidentSection section = PlayerController.getLoaded(uuid).getPDSection(ResidentSection.class).join();
        section.value = 999L;
        section.markDirty();

        //storage down: the quit-flush fails and the player is enqueued for retry (not dropped)
        FailableStorage.FAIL_WRITES.set(true);
        PlayerController.handlePlayerQuit(uuid);
        //wait for the retry to be ENQUEUED, not for the section to be dirty: it already is, so that
        //predicate holds before the flush even starts and the assertion below then reads back inside
        //the window where the flush has cleared the flag and the failed write has not re-set it yet.
        //The enqueue happens after that re-set, so it is the first instant the settled state can be
        //read - and no tick drains the queue behind a test, so the signal never goes back.
        awaitUntil(Duration.ofSeconds(3), () -> PlayerController.get().lifecycleEngine().retryBacklogSize() > 0);
        assertTrue(PlayerController.getLoadedSection(uuid, ResidentSection.class).isDirty(),
                "a storage-down quit-flush must leave the section dirty (not dropped)");
        assertFalse(PlayerController.get().getBinding(ResidentSection.class).getRepository().exists(uuid).join(),
                "nothing must have been persisted while storage was down");

        //storage returns: draining the retry queue re-flushes and the write lands. Drain INSIDE the
        //wait loop (like the periodic tick would): the failing quit-flush enqueues its retry
        //asynchronously, so a single early drain could sweep a still-empty queue and stall forever
        FailableStorage.FAIL_WRITES.set(false);
        awaitUntil(Duration.ofSeconds(10), () -> {
            PlayerController.get().drainFlushRetryQueue();
            return PlayerController.get().getBinding(ResidentSection.class).getRepository().exists(uuid).join();
        });

        assertTrue(PlayerController.get().getBinding(ResidentSection.class).getRepository().exists(uuid).join(),
                "the retried quit-flush must persist once storage returns");
        assertEquals(999L, PlayerController.get().getBinding(ResidentSection.class)
                .getRepository().find(uuid).join().orElseThrow().value);
    }

    // ------------------------------------------------------------------
    // setPlayer no longer dirties the base
    // ------------------------------------------------------------------

    @Test
    void setPlayerDoesNotDirtyBase() throws IOException {
        PlayerController.initialize(Storages.h2("f_setplayer").writeTo(tempDir));

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Attach").join();
        //creation persisted the base once; a fresh flush leaves it clean
        PlayerController.get().flushAll().join();
        assertFalse(playerData.isDirty(), "a freshly flushed base is clean");

        //attaching/detaching the live player must not dirty the base (presence is a volatile heartbeat)
        playerData.setPlayer(null);
        assertFalse(playerData.isDirty(), "setPlayer(null) must NOT mark the base dirty");
    }

    // ------------------------------------------------------------------
    // maxCached(n) bounds the cached size
    // ------------------------------------------------------------------

    @Test
    void maxCachedBoundsCachedSize() throws IOException {
        PlayerController.initialize(Storages.h2("f_lru").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BoundedSection.class, "bounded")
                .maxCached(3).build());

        //seed 10 distinct section cells; the bounded LRU keeps at most 3
        for (int i = 0; i < 10; i++) {
            UUID uuid = UUID.randomUUID();
            PlayerController.handleLogin(uuid, "L" + i).join();
            BoundedSection s = PlayerController.getLoaded(uuid).getPDSection(BoundedSection.class).join();
            s.value = i;
            s.markDirty();
        }
        PlayerController.get().flushAll().join();

        int cached = PlayerController.get().getBinding(BoundedSection.class).getManager().cachedSize();
        assertTrue(cached <= 3, "a maxCached(3) manager must cap the cached size at 3, was " + cached);
    }

    // ------------------------------------------------------------------
    // the idle sweep releases a cell whose owner never was online (no quit to key off)
    // ------------------------------------------------------------------

    @Test
    void idleSweepReleasesCellOfOfflineOwner() throws Exception {
        PlayerController.initialize(Storages.h2("f_idle").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, LazySection.class, "lazy")
                .lifecycle(SectionLifecycle.LAZY).idleGrace(Duration.ZERO).build());

        UUID uuid = UUID.randomUUID();
        //handleLogin resolves the PlayerData but never attaches an FPlayer, so the owner is OFFLINE
        //here: exactly the cell no quit event will ever release
        PlayerController.handleLogin(uuid, "Idle").join();
        LazySection s = PlayerController.getLoaded(uuid).getPDSection(LazySection.class).join();
        s.value = 1L;
        s.markDirty();
        PlayerController.get().flushAll().join();

        assertTrue(PlayerController.get().getBinding(LazySection.class).getManager().cachedSize() >= 1,
                "the resolved cell must be cached before the sweep");

        //first sweep records the cell as idle, the second releases it (grace zero)
        PlayerController.get().sweepIdleSections();
        PlayerController.get().sweepIdleSections();
        assertEquals(0, PlayerController.get().getBinding(LazySection.class).getManager().cachedSize(),
                "the idle sweep must release the cell of an offline owner");
    }

    // ------------------------------------------------------------------
    // LAZY does not load at login; ONLINE does
    // ------------------------------------------------------------------

    @Test
    void lazyDoesNotLoadOnLoginButOnlineDoes() throws IOException {
        PlayerController.initialize(Storages.h2("f_lazyvsonline").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, LazySection.class, "lazy")
                .lifecycle(SectionLifecycle.LAZY).build());
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, OnlineSection.class, "online")
                .lifecycle(SectionLifecycle.ONLINE).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Fresh").join();

        assertNull(PlayerController.getLoadedSection(uuid, LazySection.class),
                "a LAZY section must not enter memory until someone asks for it");
        assertNotNull(PlayerController.getLoadedSection(uuid, OnlineSection.class),
                "an ONLINE section is resolved by the login itself");

        //the first effective call is what loads a LAZY one
        PlayerController.getLoaded(uuid).getPDSection(LazySection.class).join();
        assertNotNull(PlayerController.getLoadedSection(uuid, LazySection.class));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private interface Condition {
        boolean test() throws Exception;
    }

    private static void awaitUntil(Duration timeout, Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.test()) return;
            Thread.sleep(20);
        }
    }

    /** Reflectively replaces a backend's Storage in the controller's registry with a failable wrapper. */
    @SuppressWarnings("unchecked")
    private static void wrapBackendWithFailable(String backendName) throws Exception {
        PlayerController controller = PlayerController.get();
        Field registryField = PlayerController.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        StorageRegistry registry = (StorageRegistry) registryField.get(controller);

        Field storagesField = StorageRegistry.class.getDeclaredField("storages");
        storagesField.setAccessible(true);
        Map<String, Storage> storages = (Map<String, Storage>) storagesField.get(registry);

        Storage real = storages.get(backendName);
        storages.put(backendName, new FailableStorage(real));
    }

    /** A Storage decorator whose write ops fail while {@link #FAIL_WRITES} is set (a storage-down simulation). */
    static final class FailableStorage implements Storage {
        static final AtomicBoolean FAIL_WRITES = new AtomicBoolean(false);
        private final Storage delegate;

        FailableStorage(Storage delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableFuture<Void> init() {
            return delegate.init();
        }

        @Override
        public CompletableFuture<Void> close() {
            return delegate.close();
        }

        @Override
        public CompletableFuture<HealthStatus> health() {
            return delegate.health();
        }

        @Override
        public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
            return new FailableRepository<>(delegate.repository(descriptor));
        }

        @Override
        public StorageLogConfig getStorageLogConfig() {
            return delegate.getStorageLogConfig();
        }

        @Override
        public Storage setStorageLogConfig(StorageLogConfig config) {
            delegate.setStorageLogConfig(config);
            return this;
        }
    }

    /** Fails save/saveAll while the outage flag is on; every read/query delegates unchanged. */
    static final class FailableRepository<K, V> implements Repository<K, V> {
        private final Repository<K, V> delegate;

        FailableRepository(Repository<K, V> delegate) {
            this.delegate = delegate;
        }

        private static <T> CompletableFuture<T> down() {
            CompletableFuture<T> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("simulated storage outage"));
            return f;
        }

        @Override
        public CompletableFuture<Void> save(V entity) {
            return FailableStorage.FAIL_WRITES.get() ? down() : delegate.save(entity);
        }

        @Override
        public CompletableFuture<Void> saveAll(Collection<V> entities) {
            return FailableStorage.FAIL_WRITES.get() ? down() : delegate.saveAll(entities);
        }

        @Override
        public CompletableFuture<Void> saveAll(Collection<V> entities, WriteMode mode) {
            return FailableStorage.FAIL_WRITES.get() ? down() : delegate.saveAll(entities, mode);
        }

        @Override
        public CompletableFuture<Optional<V>> find(K key) {
            return delegate.find(key);
        }

        @Override
        public CompletableFuture<List<V>> findMany(Collection<K> keys) {
            return delegate.findMany(keys);
        }

        @Override
        public CompletableFuture<Boolean> delete(K key) {
            return delegate.delete(key);
        }

        @Override
        public CompletableFuture<Boolean> exists(K key) {
            return delegate.exists(key);
        }

        @Override
        public CompletableFuture<Long> count() {
            return delegate.count();
        }

        @Override
        public CompletableFuture<Map<K, Long>> versions(Collection<K> keys) {
            return delegate.versions(keys);
        }

        @Override
        public CompletableFuture<Stream<V>> all() {
            return delegate.all();
        }

        @Override
        public CompletableFuture<List<V>> findBy(String fieldPath, Object value) {
            return delegate.findBy(fieldPath, value);
        }

        @Override
        public CompletableFuture<List<V>> query(Query query, QueryOptions options) {
            return delegate.query(query, options);
        }

        @Override
        public CompletableFuture<Slice<ScanRow<V>>> scanAll(Cursor cursor, int limit) {
            return delegate.scanAll(cursor, limit);
        }

        @Override
        public CompletableFuture<Slice<String>> keys(Cursor cursor, int limit) {
            return delegate.keys(cursor, limit);
        }

        @Override
        public CompletableFuture<Slice<V>> queryAfter(Query query, Cursor cursor, int limit) {
            return delegate.queryAfter(query, cursor, limit);
        }
    }
}
