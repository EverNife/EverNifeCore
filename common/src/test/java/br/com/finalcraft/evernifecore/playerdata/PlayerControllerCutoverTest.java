package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.WriteMode;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import br.com.finalcraft.everydatabase.query.Cursor;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.query.QueryOptions;
import br.com.finalcraft.everydatabase.query.ScanRow;
import br.com.finalcraft.everydatabase.query.Slice;
import br.com.finalcraft.everydatabase.versioned.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.time.Instant;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlayerData cutover integration tests: boot -> create -> mark dirty -> flush ->
 * new instance -> data intact; rename; lazy-load in RECENT mode; unit tests for the
 * conflict handler.
 * Runs on LocalFile (yaml) and H2 mem - no Docker or external services.
 */
@ECoreTest
class PlayerControllerCutoverTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class JobsPDSection extends PDSection {
        public int level;
        public String job = "none";
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------



    // ------------------------------------------------------------------
    // boot -> create -> dirty -> flush -> new instance -> data intact
    // ------------------------------------------------------------------

    @Test
    void bootCreateFlushRebootKeepsData_onLocalFileYaml() throws IOException {
        File storageYml = Storages.localFile().writeTo(tempDir);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerData created = PlayerController.handleLogin(uuid, "Petrus").join();
        assertEquals("Petrus", created.getName());
        long firstSeen = created.getFirstSeen();

        created.markDirty();
        PlayerController.get().flushAll().join();

        //second bootstrap on the same file = atomic swap (the old instance flushes and closes)
        PlayerController.initialize(storageYml);

        PlayerData reloaded = PlayerController.getLoaded(uuid);
        assertNotNull(reloaded, "PlayerData must be loaded by the new instance (load-mode ALL)");
        assertEquals("Petrus", reloaded.getName());
        assertEquals(firstSeen, reloaded.getFirstSeen());
        assertTrue(reloaded.getLastSaved() > 0, "lastSaved must have been materialized by the flush");

        //UUIDsController repopulated from storage
        assertEquals(uuid, UUIDsController.getUUIDFromName("Petrus"));
    }

    @Test
    void pdSectionRoundTripWithHotLoad_onH2() throws IOException {
        File storageYml = Storages.h2("cutover_sections").writeTo(tempDir);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Nife").join();

        //register AFTER the players are loaded: the hot-load batch attaches a default instance
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, JobsPDSection.class, "jobs").build());

        PlayerData playerData = PlayerController.getLoaded(uuid);
        JobsPDSection section = playerData.getPDSection(JobsPDSection.class).join();
        assertEquals(0, section.level);
        assertSame(playerData, section.getPlayerData());
        assertEquals(uuid, section.getUniqueId());

        section.level = 42;
        section.job = "miner";
        section.markDirty();
        PlayerController.get().flushAll().join();

        //reboot: REGISTERED_SECTIONS survives, the hot-load brings back the stored section
        PlayerController.initialize(storageYml);

        JobsPDSection reloaded = PlayerController.getLoaded(uuid).getPDSection(JobsPDSection.class).join();
        assertEquals(42, reloaded.level);
        assertEquals("miner", reloaded.job);
        assertEquals(uuid, reloaded.getUniqueId());

        //the auto-generated entry in storage.yml exists and is idempotent
        String yml = new String(Files.readAllBytes(storageYml.toPath()), StandardCharsets.UTF_8);
        assertTrue(yml.contains("jobs:"), "registerPDSectionCfg must append the pdsections entry");
    }

    // ------------------------------------------------------------------
    // rename (no files involved, just re-mapping)
    // ------------------------------------------------------------------

    @Test
    void renameOnLoginRemapsNameAndMarksDirty() throws IOException {
        File storageYml = Storages.localFile().writeTo(tempDir);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "OldName").join();
        PlayerController.get().flushAll().join();

        PlayerData renamed = PlayerController.handleLogin(uuid, "NewName").join();
        assertEquals("NewName", renamed.getName());
        assertTrue(renamed.isDirty(), "rename must mark the player dirty");
        assertEquals(uuid, UUIDsController.getUUIDFromName("NewName"));
        assertNull(UUIDsController.getUUIDFromName("OldName"), "old name link must be gone");

        //the rename survives a flush + reboot
        PlayerController.get().flushAll().join();
        PlayerController.initialize(storageYml);
        assertEquals("NewName", PlayerController.getLoaded(uuid).getName());
    }

    // ------------------------------------------------------------------
    // load-mode RECENT + lazy-load
    // ------------------------------------------------------------------

    @Test
    void recentLoadModeSkipsOldPlayersAndLazyLoadsThem_onH2() throws IOException {
        String db = "cutover_recent";
        File allYml = Storages.h2(db).loadModeAll().fileName("storage_all.yml").writeTo(tempDir);
        PlayerController.initialize(allYml);

        UUID recentUuid = UUID.randomUUID();
        UUID oldUuid = UUID.randomUUID();
        PlayerController.handleLogin(recentUuid, "RecentGuy").join();
        PlayerData oldPlayer = PlayerController.handleLogin(oldUuid, "OldGuy").join();

        //age the old player (direct field access - same package; the player is offline,
        //so the flush keeps this lastSeen instead of materializing 'now')
        oldPlayer.lastSeen = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(120);
        oldPlayer.markDirty();
        PlayerController.get().flushAll().join();
        PlayerController.shutdown();

        File recentYml = Storages.h2(db).loadModeRecent(60).fileName("storage_recent.yml").writeTo(tempDir);
        PlayerController.initialize(recentYml);

        assertNotNull(PlayerController.getLoaded(recentUuid), "recently seen player must be eager-loaded");
        assertNull(PlayerController.getLoaded(oldUuid), "old player must NOT be eager-loaded on RECENT");

        //lazy-load on demand (memory or storage)
        PlayerData lazyLoaded = PlayerController.getPlayerData(oldUuid).join();
        assertNotNull(lazyLoaded);
        assertEquals("OldGuy", lazyLoaded.getName());
        assertSame(lazyLoaded, PlayerController.getLoaded(oldUuid), "lazy-loaded player joins the live map");
    }

    // ------------------------------------------------------------------
    // payload hygiene: runtime state must never leak into the entity payload
    // ------------------------------------------------------------------

    @Test
    void runtimeStateDoesNotLeakIntoThePayload() {
        PlayerData playerData = new PlayerData(UUID.randomUUID(), "Clean");

        String json = new String(new JacksonJsonCodec<>(PlayerData.class).encode(playerData), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"name\":\"Clean\""));
        assertFalse(json.contains("\"player\""), "runtime FPlayer must not leak");
        assertFalse(json.contains("recentChanged"), "dirty flag must not leak");
        assertFalse(json.contains("sections"), "section cache must not leak");
        assertFalse(json.contains("cooldown"), "the removed player-cooldown flow must not leak");

        //round-trip: decode + warnIfStaleSchema rebuilds a functional aggregate
        PlayerData decoded = new JacksonJsonCodec<>(PlayerData.class).decode(json.getBytes(StandardCharsets.UTF_8));
        decoded.warnIfStaleSchema();
        assertEquals(playerData.getUniqueId(), decoded.getUniqueId());
        assertEquals("Clean", decoded.getName());
        assertEquals(playerData.getFirstSeen(), decoded.getFirstSeen());
    }

    // ------------------------------------------------------------------
    // ADOPT_WINNER conflict handling (unit - H2/LocalFile/InMemory never conflict,
    // so a scripted FakeRepository forces the OptimisticLockException)
    // ------------------------------------------------------------------

    private EntityDescriptor<UUID, PlayerData> testDescriptor() {
        return EntityDescriptor.builder(UUID.class, PlayerData.class)
                .collection("conflict_test")
                .keyExtractor(PlayerData::getUniqueId)
                .codec(new JacksonJsonCodec<>(PlayerData.class))
                .build();
    }

    /**
     * The mechanism the flush path relies on: {@code saveAllAndCache} never rethrows; it evicts the
     * conflicting cell and reports it via {@code conflictedKeys()}. The controller then re-reads the
     * winner, {@code adoptStoredState}s it into the SAME live instance and {@code seedIfAbsent}s that
     * instance back - so a plugin-held reference stays canonical after a conflict.
     */
    @Test
    void baseConflictReAdoptsWinnerIntoSameInstance_canonicalIdentityPreserved() {
        UUID uuid = UUID.randomUUID();
        PlayerData live = new PlayerData(uuid, "Local");
        live.markDirty();

        PlayerData winner = new PlayerData(uuid, "Winner");
        winner.lockVersion = 9L;

        FakeRepository repository = new FakeRepository();
        repository.stored = winner;
        repository.failSavesWith(() -> new OptimisticLockException(PlayerData.class, uuid, 1, 9));

        EntityDescriptor<UUID, PlayerData> descriptor = testDescriptor();
        RefRegistry registry = new RefRegistry();
        CachingManager<UUID, PlayerData> manager =
                registry.manager(descriptor, new FakeStorage(repository), CachePolicy.always());
        manager.seedIfAbsent(uuid, live);

        var report = manager.saveAllAndCache(List.of(live)).join();
        assertTrue(report.conflictedKeys().contains(uuid), "the conflict must be reported, not thrown");

        //reproduce the controller's ADOPT_WINNER resolution
        PlayerData stored = manager.repository().find(uuid).join().orElseThrow();
        live.adoptStoredState(stored);
        PlayerData canonical = manager.seedIfAbsent(uuid, live);

        assertSame(live, canonical, "the SAME live instance must remain canonical after a conflict");
        assertEquals("Winner", live.getName(), "the stored winner's state was adopted into the live object");
        assertEquals(9L, live.lockVersion, "the winner's version counter was adopted");
    }

    /** The section conflict path is symmetric to the base: same fetch -> adopt -> reinstall shape. */
    @Test
    void sectionConflictReAdoptsWinnerIntoSameInstance_symmetricToBase() {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = new JobsPDSection();
        live.uuid = uuid;
        live.level = 1;
        live.job = "local";
        live.markDirty();

        JobsPDSection winner = new JobsPDSection();
        winner.uuid = uuid;
        winner.level = 99;
        winner.job = "winner";
        winner.lockVersion = 5L;

        FakeSectionRepository repository = new FakeSectionRepository();
        repository.stored = winner;
        repository.failSavesWith(() -> new OptimisticLockException(JobsPDSection.class, uuid, 1, 5));

        EntityDescriptor<UUID, JobsPDSection> descriptor = EntityDescriptor
                .builder(UUID.class, JobsPDSection.class)
                .collection("conflict_test_section")
                .keyExtractor(JobsPDSection::getUniqueId)
                .codec(new JacksonJsonCodec<>(JobsPDSection.class))
                .build();
        RefRegistry registry = new RefRegistry();
        CachingManager<UUID, JobsPDSection> manager =
                registry.manager(descriptor, new FakeSectionStorage(repository), CachePolicy.always());
        manager.seedIfAbsent(uuid, live);

        var report = manager.saveAllAndCache(List.of(live)).join();
        assertTrue(report.conflictedKeys().contains(uuid), "the section conflict must be reported, not thrown");

        JobsPDSection stored = manager.repository().find(uuid).join().orElseThrow();
        live.adoptStoredState(stored);
        JobsPDSection canonical = manager.seedIfAbsent(uuid, live);

        assertSame(live, canonical, "the SAME live section instance must remain canonical after a conflict");
        assertEquals(99, live.level, "the stored winner's fields were adopted into the live section");
        assertEquals("winner", live.job);
        assertEquals(5L, live.lockVersion, "the winner's version counter was adopted");
    }

    /** adoptStoredState copies persisted fields but never touches the runtime wiring. */
    @Test
    void sectionAdoptStoredStateCopiesPersistedFieldsNotRuntimeWiring() {
        UUID uuid = UUID.randomUUID();
        PlayerData playerData = new PlayerData(uuid, "Owner");

        JobsPDSection live = new JobsPDSection();
        live.attachPlayerData(playerData);
        live.level = 1;
        live.markDirty();

        JobsPDSection stored = new JobsPDSection();
        stored.uuid = uuid;
        stored.level = 42;
        stored.job = "miner";
        stored.lockVersion = 3L;

        live.adoptStoredState(stored);

        assertEquals(42, live.level, "persisted field copied");
        assertEquals("miner", live.job, "persisted field copied");
        assertEquals(3L, live.lockVersion, "version counter copied");
        assertSame(playerData, live.getPlayerData(), "runtime wiring (playerData) must be preserved");
    }

    /**
     * The re-dirty branch of the section conflict resolution: when the live instance was modified
     * again while the winner was being re-read, only the winner's lock version is adopted and the
     * LOCAL field values are kept, so the next flush overwrites the remote row instead of losing them.
     */
    @Test
    void sectionReDirtiedDuringResolutionKeepsLocalValuesAdoptsOnlyLockVersion() {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = new JobsPDSection();
        live.uuid = uuid;
        live.level = 777;
        live.job = "kept-local";
        live.markDirty(); //re-dirtied while the winner is resolved

        JobsPDSection winner = new JobsPDSection();
        winner.uuid = uuid;
        winner.level = 99;
        winner.job = "winner";
        winner.lockVersion = 11L;

        //the re-dirty path takes ONLY the winner's lock version, never its values
        live.adoptStoredLockVersion(winner);

        assertEquals(777, live.level, "a re-dirty during resolution keeps the LOCAL values");
        assertEquals("kept-local", live.job);
        assertEquals(11L, live.lockVersion, "only the winner's lock version is adopted on a re-dirty");
        assertTrue(live.isDirty(), "the re-dirtied section stays dirty (flushed again next tick)");
    }

    /** Minimal FakeStorage returning a scripted repository, so a real CachingManager can be built. */
    private static final class FakeStorage implements br.com.finalcraft.everydatabase.Storage {
        private final Repository<UUID, PlayerData> repository;
        private br.com.finalcraft.everydatabase.log.StorageLogConfig logConfig =
                br.com.finalcraft.everydatabase.log.StorageLogConfig.silent();
        FakeStorage(Repository<UUID, PlayerData> repository) { this.repository = repository; }
        @Override public CompletableFuture<Void> init() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> close() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<br.com.finalcraft.everydatabase.HealthStatus> health() {
            return CompletableFuture.completedFuture(br.com.finalcraft.everydatabase.HealthStatus.ok(0));
        }
        @Override @SuppressWarnings("unchecked")
        public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
            return (Repository<K, V>) repository;
        }
        @Override public br.com.finalcraft.everydatabase.log.StorageLogConfig getStorageLogConfig() { return logConfig; }
        @Override public br.com.finalcraft.everydatabase.Storage setStorageLogConfig(
                br.com.finalcraft.everydatabase.log.StorageLogConfig config) { this.logConfig = config; return this; }
    }

    private static final class FakeSectionStorage implements br.com.finalcraft.everydatabase.Storage {
        private final Repository<UUID, JobsPDSection> repository;
        private br.com.finalcraft.everydatabase.log.StorageLogConfig logConfig =
                br.com.finalcraft.everydatabase.log.StorageLogConfig.silent();
        FakeSectionStorage(Repository<UUID, JobsPDSection> repository) { this.repository = repository; }
        @Override public CompletableFuture<Void> init() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> close() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<br.com.finalcraft.everydatabase.HealthStatus> health() {
            return CompletableFuture.completedFuture(br.com.finalcraft.everydatabase.HealthStatus.ok(0));
        }
        @Override @SuppressWarnings("unchecked")
        public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
            return (Repository<K, V>) repository;
        }
        @Override public br.com.finalcraft.everydatabase.log.StorageLogConfig getStorageLogConfig() { return logConfig; }
        @Override public br.com.finalcraft.everydatabase.Storage setStorageLogConfig(
                br.com.finalcraft.everydatabase.log.StorageLogConfig config) { this.logConfig = config; return this; }
    }

    /** Scriptable base repository: fails saves with a conflict, serves the stored winner on find. */
    private static final class FakeRepository implements Repository<UUID, PlayerData> {

        final AtomicInteger saveCalls = new AtomicInteger();
        PlayerData stored;
        private java.util.function.Supplier<? extends Throwable> everySaveError;

        void failSavesWith(java.util.function.Supplier<? extends Throwable> error) {
            this.everySaveError = error;
        }

        private CompletableFuture<Void> nextSaveResult() {
            saveCalls.incrementAndGet();
            CompletableFuture<Void> future = new CompletableFuture<>();
            if (everySaveError != null) future.completeExceptionally(everySaveError.get());
            else future.complete(null);
            return future;
        }

        @Override public CompletableFuture<Optional<PlayerData>> find(UUID key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored));
        }
        @Override public CompletableFuture<List<PlayerData>> findMany(Collection<UUID> keys) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        @Override public CompletableFuture<Void> save(PlayerData entity) { return nextSaveResult(); }
        @Override public CompletableFuture<Void> saveAll(Collection<PlayerData> entities) { return nextSaveResult(); }
        @Override public CompletableFuture<Void> saveAll(Collection<PlayerData> entities, WriteMode mode) {
            if (mode == null || mode == WriteMode.UPSERT) return saveAll(entities);
            throw new UnsupportedOperationException();
        }
        @Override public CompletableFuture<Boolean> delete(UUID key) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletableFuture<Boolean> exists(UUID key) {
            return CompletableFuture.completedFuture(stored != null);
        }
        @Override public CompletableFuture<Long> count() {
            return CompletableFuture.completedFuture(stored == null ? 0L : 1L);
        }
        @Override public CompletableFuture<Stream<PlayerData>> all() {
            return CompletableFuture.completedFuture(stored == null ? Stream.empty() : Stream.of(stored));
        }
        @Override public CompletableFuture<List<PlayerData>> findBy(String fieldPath, Object value) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        @Override public CompletableFuture<List<PlayerData>> query(Query query, QueryOptions options) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        @Override public CompletableFuture<java.util.Map<UUID, Long>> versions(Collection<UUID> keys) {
            return CompletableFuture.completedFuture(new java.util.HashMap<>());
        }
        @Override public CompletableFuture<Slice<String>> keys(Cursor cursor, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public CompletableFuture<Slice<ScanRow<PlayerData>>> scanAll(Cursor cursor, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public CompletableFuture<Slice<PlayerData>> queryAfter(Query query, Cursor cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    /** Scriptable section repository: same shape as {@link FakeRepository} for a PDSection type. */
    private static final class FakeSectionRepository implements Repository<UUID, JobsPDSection> {

        JobsPDSection stored;
        private java.util.function.Supplier<? extends Throwable> everySaveError;

        void failSavesWith(java.util.function.Supplier<? extends Throwable> error) {
            this.everySaveError = error;
        }

        private CompletableFuture<Void> nextSaveResult() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            if (everySaveError != null) future.completeExceptionally(everySaveError.get());
            else future.complete(null);
            return future;
        }

        @Override public CompletableFuture<Optional<JobsPDSection>> find(UUID key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored));
        }
        @Override public CompletableFuture<List<JobsPDSection>> findMany(Collection<UUID> keys) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        @Override public CompletableFuture<Void> save(JobsPDSection entity) { return nextSaveResult(); }
        @Override public CompletableFuture<Void> saveAll(Collection<JobsPDSection> entities) { return nextSaveResult(); }
        @Override public CompletableFuture<Void> saveAll(Collection<JobsPDSection> entities, WriteMode mode) {
            if (mode == null || mode == WriteMode.UPSERT) return saveAll(entities);
            throw new UnsupportedOperationException();
        }
        @Override public CompletableFuture<Boolean> delete(UUID key) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletableFuture<Boolean> exists(UUID key) {
            return CompletableFuture.completedFuture(stored != null);
        }
        @Override public CompletableFuture<Long> count() {
            return CompletableFuture.completedFuture(stored == null ? 0L : 1L);
        }
        @Override public CompletableFuture<Stream<JobsPDSection>> all() {
            return CompletableFuture.completedFuture(stored == null ? Stream.empty() : Stream.of(stored));
        }
        @Override public CompletableFuture<List<JobsPDSection>> findBy(String fieldPath, Object value) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        @Override public CompletableFuture<List<JobsPDSection>> query(Query query, QueryOptions options) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        @Override public CompletableFuture<java.util.Map<UUID, Long>> versions(Collection<UUID> keys) {
            return CompletableFuture.completedFuture(new java.util.HashMap<>());
        }
        @Override public CompletableFuture<Slice<String>> keys(Cursor cursor, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public CompletableFuture<Slice<ScanRow<JobsPDSection>>> scanAll(Cursor cursor, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public CompletableFuture<Slice<JobsPDSection>> queryAfter(Query query, Cursor cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    // ------------------------------------------------------------------
    //  payloads of java.time and Optional types
    // ------------------------------------------------------------------

    /** A section whose persisted state includes a {@code java.time.Instant}, a {@code LocalDateTime} and an {@code Optional<String>}. */
    public static class TemporalPDSection extends PDSection {
        public Instant lastReward = Instant.EPOCH;
        public LocalDateTime joinedAt;
        public Optional<String> nickname = Optional.empty();
    }


    // ------------------------------------------------------------------
    // java.time / Optional round-trip on a real backend (jsr310 + jdk8 modules)
    // ------------------------------------------------------------------

    @Test
    void javaTimeAndOptionalFieldsSurviveRoundTrip_onH2() throws IOException {
        File storageYml = Storages.h2("g1_temporal").writeTo(tempDir);
        PlayerController.initialize(storageYml);
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, TemporalPDSection.class, "temporalpdsection").build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Chronos").join();

        Instant rewardAt = Instant.ofEpochMilli(1_700_000_123_456L);
        LocalDateTime joined = LocalDateTime.of(2023, 11, 14, 8, 55, 23, 456_000_000);

        TemporalPDSection section = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        section.lastReward = rewardAt;
        section.joinedAt = joined;
        section.nickname = Optional.of("The Timekeeper");
        section.markDirty();
        PlayerController.get().flushAll().join();

        //reboot: the section is re-read from the backend through the codec
        PlayerController.initialize(storageYml);

        TemporalPDSection reloaded = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        assertEquals(rewardAt, reloaded.lastReward, "java.time.Instant must survive the codec round-trip");
        assertEquals(joined, reloaded.joinedAt, "LocalDateTime must survive the codec round-trip");
        assertTrue(reloaded.nickname.isPresent(), "a present Optional must survive the codec round-trip");
        assertEquals("The Timekeeper", reloaded.nickname.get());
    }

    @Test
    void absentOptionalAndDefaultInstantSurviveRoundTrip_onLocalFileYaml() throws IOException {
        File storageYml = Storages.localFile().writeTo(tempDir);
        PlayerController.initialize(storageYml);
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, TemporalPDSection.class, "temporalpdsection").build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Blank").join();

        //leave nickname absent and joinedAt null, mutate only the Instant so a row is written
        TemporalPDSection section = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        section.lastReward = Instant.ofEpochSecond(42);
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.initialize(storageYml);

        TemporalPDSection reloaded = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        assertEquals(Instant.ofEpochSecond(42), reloaded.lastReward);
        assertFalse(reloaded.nickname.isPresent(), "an absent Optional must round-trip as Optional.empty(), not null");
    }
}
