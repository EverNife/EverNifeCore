package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.PlayerControllerConflictPipelineTest.ConflictInjectingStorage;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.changefeed.ChangeEvent;
import br.com.finalcraft.everydatabase.changefeed.ChangeOp;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The network hooks of a section: {@code onFlushed} fires for a row this server WROTE and only then,
 * {@code onRemoteChange} for a row ANOTHER server changed and only then, and {@code readStoredVersion} reads the
 * backend's version. Runs over the memory backend, whose change feed puts the cache-sync on.
 */
@ECoreTest
class PlayerControllerSectionHooksTest {

    private static final long WAIT_SECONDS = 5;

    @TempDir
    Path tempDir;

    private PlayerDataWorld world;

    public static class HookedSection extends PDSection {
        public int level;

        @JsonIgnore
        transient AtomicInteger flushed = new AtomicInteger();
        @JsonIgnore
        transient List<ChangeEvent> remoteChanges = new CopyOnWriteArrayList<>();

        @Override
        protected void onFlushed() {
            flushed.incrementAndGet();
        }

        @Override
        protected void onRemoteChange(ChangeEvent event) {
            remoteChanges.add(event);
        }
    }

    @BeforeEach
    void boot() {
        world = PlayerDataWorld.with(Storages.memory()).boot(tempDir);
    }

    @AfterEach
    void close() {
        ConflictInjectingStorage.reset();
        world.close();
    }

    /** Registered after the boot, so a test can first put the conflict decorator under the section. */
    private static void register() {
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, HookedSection.class, "hookedsection").build());
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static HookedSection login(UUID uuid, String name) throws Exception {
        await(PlayerController.handleLogin(uuid, name));
        return await(PlayerController.getLoaded(uuid).getPDSection(HookedSection.class));
    }

    /** A row that exists in the backend, flushed once. */
    private HookedSection stored(UUID uuid) throws Exception {
        HookedSection section = login(uuid, "Worker");
        section.level = 1;
        section.markDirty();
        world.flushAll();
        return section;
    }

    private static CachingManager<UUID, HookedSection> manager() {
        return PlayerController.get().getBinding(HookedSection.class).getManager();
    }

    // ------------------------------------------------------------------
    //  onFlushed
    // ------------------------------------------------------------------

    @Test
    void onFlushedRunsOnceWhenTheFlushWritesTheDirtyRow() throws Exception {
        register();
        HookedSection section = login(UUID.randomUUID(), "Worker");
        world.flushAll();
        assertEquals(0, section.flushed.get(), "a clean row is not written, so nothing was flushed");

        section.level = 3;
        section.markDirty();
        world.flushAll();
        world.flushAll();

        assertEquals(1, section.flushed.get(), "one write, one call - the second flush had nothing to write");
    }

    @Test
    void onFlushedRunsAfterForceSave() throws Exception {
        register();
        HookedSection section = login(UUID.randomUUID(), "Worker");

        section.level = 7;
        await(section.forceSave());

        assertEquals(1, section.flushed.get());
        assertFalse(section.isDirty());
    }

    @Test
    void onFlushedSkipsARowThatLostTheConflictUntilItsRetryLands() throws Exception {
        //no backend here enforces the optimistic lock, so the race is scripted under the section
        PlayerControllerConflictPipelineTest.wrapBackend("test_memory");
        ConflictInjectingStorage.scripting(HookedSection.class);
        register();
        UUID uuid = UUID.randomUUID();
        HookedSection live = stored(uuid);
        assertEquals(1, live.flushed.get());

        HookedSection winner = new HookedSection();
        winner.uuid = uuid;
        winner.level = 99;
        winner.lockVersion = 7L;
        ConflictInjectingStorage.serveWinnerAndConflict(winner);

        live.level = 5;
        live.markDirty();
        world.flushAll();
        assertEquals(99, live.level, "the lost race adopted the winner");
        assertEquals(1, live.flushed.get(), "this server wrote nothing, so the plugin must not hear 'flushed'");

        ConflictInjectingStorage.reset();
        live.level = 6;
        live.markDirty();
        world.flushAll();
        assertEquals(2, live.flushed.get(), "the retry over the adopted version landed");
    }

    // ------------------------------------------------------------------
    //  onRemoteChange
    // ------------------------------------------------------------------

    @Test
    void onRemoteChangeReachesTheLiveInstanceAndThenTheCellGoesStale() throws Exception {
        register();
        UUID uuid = UUID.randomUUID();
        HookedSection live = stored(uuid);
        assertTrue(manager().isCached(uuid));

        world.remoteChange(HookedSection.class, uuid, ChangeOp.SAVE, 42L);

        assertEquals(1, live.remoteChanges.size(), "the instance the cache held is the one told");
        ChangeEvent event = live.remoteChanges.get(0);
        assertEquals(ChangeOp.SAVE, event.op());
        assertEquals(42L, event.version());
        assertEquals(uuid.toString(), event.key());
        assertFalse(manager().isCached(uuid), "after the hook the cell is stale: the next read reloads");
    }

    @Test
    void ownOriginWritesNeverCallOnRemoteChange() throws Exception {
        register();
        UUID uuid = UUID.randomUUID();
        HookedSection live = stored(uuid);
        live.level = 2;
        live.markDirty();
        world.flushAll();

        assertTrue(live.remoteChanges.isEmpty(), "this server's own writes came back on the feed and were skipped");
        assertTrue(manager().isCached(uuid), "and the own write did not stale the cell either");

        //the same feed DOES carry another server's write - so the silence above is the skip, not a dead wire
        world.remoteChange(HookedSection.class, uuid, ChangeOp.SAVE, 9L);
        assertEquals(1, live.remoteChanges.size());
    }

    // ------------------------------------------------------------------
    //  readStoredVersion / getLockVersion
    // ------------------------------------------------------------------

    @Test
    void readStoredVersionAnswersTheBackendAndNullForAMissingRow() throws Exception {
        register();
        UUID uuid = UUID.randomUUID();
        HookedSection persisted = stored(uuid);

        //another writer stamps a version behind this server's cache: only the backend knows it
        Repository<UUID, HookedSection> repository = PlayerController.get().getBinding(HookedSection.class).getRepository();
        HookedSection other = await(repository.find(uuid)).get();
        other.lockVersion = 7L;
        await(repository.save(other));

        assertEquals(Long.valueOf(7L), await(persisted.readStoredVersion()), "the backend's version, not the instance's");
        assertNull(persisted.getLockVersion(), "the live instance never adopted it");

        HookedSection neverWritten = login(UUID.randomUUID(), "Visitor");
        assertNull(neverWritten.getLockVersion());
        assertNull(await(neverWritten.readStoredVersion()), "a transient default has no row in the backend");
    }
}
