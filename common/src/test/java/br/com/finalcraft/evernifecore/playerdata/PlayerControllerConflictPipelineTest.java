package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.manager.writeback.OptimisticConflictException;
import br.com.finalcraft.everydatabase.manager.writeback.StorageWriteException;
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
import br.com.finalcraft.everydatabase.versioned.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the caller-initiated {@code forceSave*} pipeline through REAL failures produced behind the
 * manager's back: a storage decorator that makes a section's write lose an optimistic-lock race, fail
 * transiently, or find no winner on the post-conflict re-read. Asserts the surfaced exception type,
 * the adopted winner state, canonical identity preservation and the re-dirty guard - end to end
 * through {@code PlayerController.flushSection}/{@code flushPlayer}, not a reproduction of it. Runs on
 * H2 mem with the section backend wrapped - no Docker.
 */
@ECoreTest
class PlayerControllerConflictPipelineTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        //stop injecting failures BEFORE shutdown so its final flush runs against the clean backend
        //(a lingering gated find could otherwise block a storage thread)
        ConflictInjectingStorage.reset();
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
    }

    public static class JobsPDSection extends PDSection {
        public int level;
        public String job = "none";
    }

    private File writeMemoryStorageYml(String name) throws IOException {
        //a memory backend keeps this test light (no retained H2 DB / pool); the injecting wrapper
        //supplies every conflict/error/vanish behavior on top of it
        String yml = String.join("\n",
                "storage-backends:",
                "  test_mem:",
                "    enabled: true",
                "    type: memory",
                "default-backend: test_mem",
                "");
        File file = tempDir.resolve("storage_" + name + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /**
     * Boots a controller whose section backend is wrapped by the conflict-injecting decorator, logs in
     * a player and returns their live (dirtied) section. The wrapper is installed after bootstrap but
     * before the section registers, so the section manager binds against the decorator.
     */
    private JobsPDSection bootWrappedSectionFor(String name, UUID uuid) throws Exception {
        PlayerController.initialize(writeMemoryStorageYml(name));
        wrapBackend("test_mem");
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, JobsPDSection.class).build());
        PlayerController.handleLogin(uuid, "Worker").join();
        JobsPDSection section = PlayerController.getLoaded(uuid).getPDSection(JobsPDSection.class).join();
        //persist a first row cleanly so the conflict has a real winner to re-read
        section.level = 1;
        section.job = "local";
        section.markDirty();
        PlayerController.get().flushAll().join();
        return section;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    // ------------------------------------------------------------------
    // forceSavePDSection surfaces an optimistic-lock conflict AND adopts the winner (production path)
    // ------------------------------------------------------------------

    @Test
    void forceSaveSectionSurfacesConflict_adoptsWinnerIntoSameLiveInstance() throws Exception {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = bootWrappedSectionFor("conf_section", uuid);
        var manager = PlayerController.get().getBinding(JobsPDSection.class).getManager();

        //the winner the post-conflict re-read will serve: newer values + a bumped lock version
        JobsPDSection winner = new JobsPDSection();
        winner.uuid = uuid;
        winner.level = 99;
        winner.job = "winner";
        winner.lockVersion = 7L;
        ConflictInjectingStorage.serveWinnerAndConflict(winner);

        //dirty the live instance again and force-save: the write loses the race
        live.level = 5;
        live.job = "local-edit";
        live.markClean(); //the live instance is CLEAN when the conflict resolves -> ADOPT_WINNER

        CompletableFuture<Void> forced = live.forceSavePDSection();
        CompletionException thrown = assertThrows(CompletionException.class, forced::join);
        Throwable cause = rootCause(thrown);
        assertTrue(cause instanceof OptimisticConflictException,
                "a lost force-save race must surface OptimisticConflictException, was " + cause);
        OptimisticConflictException conflict = (OptimisticConflictException) cause;
        assertTrue(conflict.getConflictedKeys().contains(uuid), "the conflicted key must be reported");

        //the winner's field values were adopted INTO the same held instance
        assertEquals(99, live.level, "the stored winner's values were adopted into the live instance");
        assertEquals("winner", live.job);
        assertEquals(7L, live.lockVersion, "the winner's lock version was adopted");

        //manager peek still returns the SAME reference (canonical identity preserved)
        assertSame(live, manager.peek(uuid).orElse(null),
                "the live instance must remain the canonical cached cell after the conflict");
    }

    // ------------------------------------------------------------------
    // forceSavePlayerData surfaces a section conflict through the whole-player path
    // ------------------------------------------------------------------

    @Test
    void forceSavePlayerDataSurfacesSectionConflict() throws Exception {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = bootWrappedSectionFor("conf_base", uuid);

        JobsPDSection winner = new JobsPDSection();
        winner.uuid = uuid;
        winner.level = 42;
        winner.job = "remote";
        winner.lockVersion = 3L;
        ConflictInjectingStorage.serveWinnerAndConflict(winner);

        //keep the section clean-valued but dirty so the whole-player flush persists (and conflicts on) it
        live.level = 8;
        live.markDirty();

        CompletableFuture<Void> forced = PlayerController.getLoaded(uuid).forceSavePlayerData();
        CompletionException thrown = assertThrows(CompletionException.class, forced::join);
        Throwable cause = rootCause(thrown);
        assertTrue(cause instanceof OptimisticConflictException,
                "the whole-player force-save must surface the section's conflict, was " + cause);
        assertEquals(42, live.level, "the winner was adopted into the section during the player force-save");
    }

    // ------------------------------------------------------------------
    // re-dirty during resolution keeps LOCAL values (only the winner's lock version is adopted)
    // ------------------------------------------------------------------

    @Test
    void reDirtyDuringResolutionKeepsLocalValues() throws Exception {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = bootWrappedSectionFor("conf_redirty", uuid);

        JobsPDSection winner = new JobsPDSection();
        winner.uuid = uuid;
        winner.level = 99;
        winner.job = "winner";
        winner.lockVersion = 11L;

        //block the post-conflict find() on a latch; while blocked, re-dirty the live instance
        CountDownLatch findEntered = new CountDownLatch(1);
        CountDownLatch releaseFind = new CountDownLatch(1);
        ConflictInjectingStorage.serveWinnerAndConflictWithGate(winner, findEntered, releaseFind);

        live.markClean();
        live.level = 5;
        live.job = "local-edit";
        live.markClean(); //enter the force-save clean, so absent the re-dirty it would ADOPT_WINNER

        CompletableFuture<Void> forced = live.forceSavePDSection();

        //wait until the resolver is inside find(), then re-dirty the live instance and release it
        assertTrue(findEntered.await(3, TimeUnit.SECONDS), "the conflict re-read must have been reached");
        live.level = 777;
        live.job = "kept-local";
        live.markDirty();
        releaseFind.countDown();

        assertThrows(CompletionException.class, forced::join); //still a lost race for the caller

        //the re-dirtied LOCAL values survived; only the winner's lock version was adopted
        assertEquals(777, live.level, "a re-dirty during resolution must keep the LOCAL values");
        assertEquals("kept-local", live.job);
        assertEquals(11L, live.lockVersion, "only the winner's lock version is adopted on a re-dirty");
        assertTrue(live.isDirty(), "the re-dirtied section stays dirty (flushes again next tick)");
    }

    // ------------------------------------------------------------------
    // a transient write failure surfaces StorageWriteException (not a conflict) and re-dirties
    // ------------------------------------------------------------------

    @Test
    void forceSaveSectionSurfacesStorageWriteExceptionOnTransientFailure() throws Exception {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = bootWrappedSectionFor("conf_write", uuid);

        ConflictInjectingStorage.failNextWritesWithError();

        live.level = 5;
        live.markDirty();
        CompletableFuture<Void> forced = live.forceSavePDSection();
        CompletionException thrown = assertThrows(CompletionException.class, forced::join);
        Throwable cause = rootCause(thrown);
        assertTrue(cause instanceof StorageWriteException,
                "a transient write failure must surface StorageWriteException, was " + cause);
        assertTrue(((StorageWriteException) cause).getFailedKeys().contains(uuid), "the failed key must be reported");
        assertTrue(live.isDirty(), "a failed force-save must leave the section dirty for the background retry");
    }

    // ------------------------------------------------------------------
    // winner vanished: the re-read finds no row -> live re-dirties and a later flush RE-CREATES the row
    // ------------------------------------------------------------------

    @Test
    void winnerVanishedDuringResolution_reCreatesRowOnNextFlush() throws Exception {
        UUID uuid = UUID.randomUUID();
        JobsPDSection live = bootWrappedSectionFor("conf_vanish", uuid);
        var repository = PlayerController.get().getBinding(JobsPDSection.class).getRepository();

        //conflict on save, but the re-read returns EMPTY (the winning row vanished)
        ConflictInjectingStorage.conflictThenVanish();

        live.level = 5;
        live.markClean();
        CompletableFuture<Void> forced = live.forceSavePDSection();
        assertThrows(CompletionException.class, forced::join); //the force-save still reports the lost race
        assertTrue(live.isDirty(), "a vanished winner must leave the live instance dirty to re-create the row");

        //storage recovers; a normal flush must re-create the row in the backend
        ConflictInjectingStorage.reset();
        PlayerController.get().flushAll().join();
        assertTrue(repository.exists(uuid).join(), "the next flush must re-create the vanished row");
    }

    // ------------------------------------------------------------------
    // reflection + decorator plumbing
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void wrapBackend(String backendName) throws Exception {
        PlayerController controller = PlayerController.get();
        Field registryField = PlayerController.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        StorageRegistry registry = (StorageRegistry) registryField.get(controller);

        Field storagesField = StorageRegistry.class.getDeclaredField("storages");
        storagesField.setAccessible(true);
        Map<String, Storage> storages = (Map<String, Storage>) storagesField.get(registry);

        Storage real = storages.get(backendName);
        storages.put(backendName, new ConflictInjectingStorage(real));
    }

    /**
     * A Storage decorator that scripts the exact failure the flush pipeline must react to. Only one
     * script is active at a time (static, single-threaded test), reset in teardown.
     */
    static final class ConflictInjectingStorage implements Storage {
        enum Mode { OFF, CONFLICT, CONFLICT_GATED, ERROR, CONFLICT_VANISH }

        private static volatile Mode mode = Mode.OFF;
        private static final AtomicReference<Object> WINNER = new AtomicReference<>();
        private static volatile CountDownLatch findEntered;
        private static volatile CountDownLatch releaseFind;

        private final Storage delegate;

        ConflictInjectingStorage(Storage delegate) {
            this.delegate = delegate;
        }

        static void reset() {
            mode = Mode.OFF;
            WINNER.set(null);
            findEntered = null;
            releaseFind = null;
        }

        static void serveWinnerAndConflict(Object winner) {
            WINNER.set(winner);
            mode = Mode.CONFLICT;
        }

        static void serveWinnerAndConflictWithGate(Object winner, CountDownLatch entered, CountDownLatch release) {
            WINNER.set(winner);
            findEntered = entered;
            releaseFind = release;
            mode = Mode.CONFLICT_GATED;
        }

        static void failNextWritesWithError() {
            mode = Mode.ERROR;
        }

        static void conflictThenVanish() {
            WINNER.set(null);
            mode = Mode.CONFLICT_VANISH;
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
            Repository<K, V> real = delegate.repository(descriptor);
            //only the section repository is scripted; the base PlayerData repo delegates untouched, so a
            //whole-player force-save conflicts on the SECTION only (and the winner type never mismatches)
            if (descriptor.type() != JobsPDSection.class) {
                return real;
            }
            return new ScriptedRepository<>(real, descriptor.keyExtractor());
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

        /** Applies the active script to save/find; everything else delegates unchanged. */
        static final class ScriptedRepository<K, V> implements Repository<K, V> {
            private final Repository<K, V> delegate;
            private final Function<V, K> keyOf;

            ScriptedRepository(Repository<K, V> delegate, Function<V, K> keyOf) {
                this.delegate = delegate;
                this.keyOf = keyOf;
            }

            private static <T> CompletableFuture<T> failed(Throwable error) {
                CompletableFuture<T> f = new CompletableFuture<>();
                f.completeExceptionally(error);
                return f;
            }

            private CompletableFuture<Void> maybeFailSave(V entity) {
                switch (mode) {
                    case CONFLICT:
                    case CONFLICT_GATED:
                    case CONFLICT_VANISH:
                        return failed(new OptimisticLockException(entity.getClass(), keyOf.apply(entity), 1, 9));
                    case ERROR:
                        return failed(new RuntimeException("simulated transient write failure"));
                    default:
                        return null;
                }
            }

            @Override
            public CompletableFuture<Void> save(V entity) {
                CompletableFuture<Void> scripted = maybeFailSave(entity);
                return scripted != null ? scripted : delegate.save(entity);
            }

            @Override
            public CompletableFuture<Void> saveAll(Collection<V> entities) {
                for (V entity : entities) {
                    CompletableFuture<Void> scripted = maybeFailSave(entity);
                    if (scripted != null) return scripted;
                }
                return delegate.saveAll(entities);
            }

            @Override
            public CompletableFuture<Void> saveAll(Collection<V> entities, WriteMode mode) {
                for (V entity : entities) {
                    CompletableFuture<Void> scripted = maybeFailSave(entity);
                    if (scripted != null) return scripted;
                }
                return delegate.saveAll(entities, mode);
            }

            @Override
            @SuppressWarnings("unchecked")
            public CompletableFuture<Optional<V>> find(K key) {
                if (mode == Mode.CONFLICT || mode == Mode.CONFLICT_GATED) {
                    if (mode == Mode.CONFLICT_GATED) {
                        //let the test observe entry, then block until it re-dirties the live instance
                        CountDownLatch entered = findEntered;
                        CountDownLatch release = releaseFind;
                        if (entered != null) entered.countDown();
                        try {
                            if (release != null) release.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    Object winner = WINNER.get();
                    return CompletableFuture.completedFuture(Optional.ofNullable((V) winner));
                }
                if (mode == Mode.CONFLICT_VANISH) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
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
            public CompletableFuture<Slice<V>> queryAfter(Query query, Cursor cursor, int limit) {
                return delegate.queryAfter(query, cursor, limit);
            }
        }
    }
}
