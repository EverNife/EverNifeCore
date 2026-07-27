package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrationMode;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaSweepMarker;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaSweeper;
import br.com.finalcraft.everydatabase.manager.entityschema.SweepOptions;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.WriteMode;
import br.com.finalcraft.everydatabase.query.IndexHint;
import br.com.finalcraft.everydatabase.query.Indexed;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.query.QueryOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrity and aggregate behaviour: the transient-default resolution contract
 * ({@code getPDSection} seeds cache-only, {@code getPDSectionIfPresent}/{@code hasPDSection}
 * distinguish absence, even with the default {@code hotLoad(true)}), delete-with-cascade and its
 * online-player guard, an indexed top-N aggregate that does not load the whole collection, a
 * non-indexed query rejection, the ahead-of-code schema-version flush refusal, static
 * {@code getPDSection} lazy-loading the player, and lazy schema upcasting on read. Runs on H2 mem -
 * no Docker.
 */
@ECoreTest
class PlayerControllerIntegrityTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
        EntitySchemaMigrations.clear();
    }

    /** A plain (uuid-keyed) section with an indexed field for the aggregate test. */
    public static class BalancePDSection extends PDSection {
        @Indexed(order = IndexHint.Order.DESCENDING)
        public long balance;
        public String note = "";
    }

    private File writeH2StorageYml(String dbName, String extraBlock) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                extraBlock,
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // getPDSection seeds a TRANSIENT default (no row until markDirty)
    // ------------------------------------------------------------------

    @Test
    void getPDSectionSeedsTransientDefault_noRowUntilMarkDirty() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_transient", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Trans").join();

        //a pure read returns a default...
        BalancePDSection section = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        assertEquals(0L, section.balance);

        //...but nothing was written to the backend (transient, cache-only seed)
        boolean rowExists = PlayerController.get().getBinding(BalancePDSection.class)
                .getRepository().exists(uuid).join();
        assertFalse(rowExists, "getPDSection must NOT write a row for a pure read (transient default)");

        //it becomes persistent only after markDirty + flush
        section.balance = 500L;
        section.markDirty();
        PlayerController.get().flushAll().join();
        assertTrue(PlayerController.get().getBinding(BalancePDSection.class)
                .getRepository().exists(uuid).join(), "a dirtied+flushed section must be persisted");
    }

    // ------------------------------------------------------------------
    // getPDSectionIfPresent distinguishes absence; hasPDSection reflects existence
    // ------------------------------------------------------------------

    @Test
    void getPDSectionIfPresentAndHasPDSection_distinguishAbsenceFromPresence() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_present", ""));
        //hotLoad(false): login does NOT auto-seed a transient default, so "absent" means a true miss
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, BalancePDSection.class).hotLoad(false).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Maybe").join();
        PlayerData playerData = PlayerController.getLoaded(uuid);

        //never-persisted section: absent, and NOT seeded by the presence read
        assertFalse(playerData.getPDSectionIfPresent(BalancePDSection.class).join().isPresent(),
                "getPDSectionIfPresent must be empty for a never-seen section");
        assertFalse(playerData.hasPDSection(BalancePDSection.class).join(),
                "hasPDSection must be false before the section exists");
        assertFalse(playerData.hasPDSectionIfLoaded(BalancePDSection.class),
                "hasPDSectionIfLoaded must be false before the section is loaded");

        //getPDSectionIfPresent must NOT have seeded a default (still a true miss on the backend)
        assertFalse(PlayerController.get().getBinding(BalancePDSection.class).getRepository().exists(uuid).join(),
                "getPDSectionIfPresent must not seed/persist a default");

        //create + persist it
        BalancePDSection section = playerData.getPDSection(BalancePDSection.class).join();
        section.balance = 42L;
        section.markDirty();
        PlayerController.get().flushAll().join();

        //now present, and the loaded peek is true
        Optional<BalancePDSection> present = playerData.getPDSectionIfPresent(BalancePDSection.class).join();
        assertTrue(present.isPresent(), "getPDSectionIfPresent returns the value once it exists");
        assertEquals(42L, present.get().balance);
        assertTrue(playerData.hasPDSection(BalancePDSection.class).join(), "hasPDSection reflects existence (async)");
        assertTrue(playerData.hasPDSectionIfLoaded(BalancePDSection.class), "loaded section peeks true (sync)");
    }

    // ------------------------------------------------------------------
    // deletePlayerData removes base AND section rows (cascade)
    // ------------------------------------------------------------------

    @Test
    void deletePlayerDataCascadesOverSections() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_delete", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Doomed").join();
        BalancePDSection section = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        section.balance = 1000L;
        section.markDirty();
        PlayerController.get().flushAll().join();

        //both the base and the section row exist
        assertTrue(PlayerController.get().getBinding(BalancePDSection.class).getRepository().exists(uuid).join());

        PlayerController.deletePlayerData(uuid).join();

        //base and section rows are both gone
        assertFalse(PlayerController.get().getBinding(BalancePDSection.class).getRepository().exists(uuid).join(),
                "deletePlayerData must cascade-delete the section row");
    }

    @Test
    void orphanReaperRemovesSectionRowsWithNoBase() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_orphan", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Ghost").join();
        BalancePDSection section = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        section.balance = 7L;
        section.markDirty();
        PlayerController.get().flushAll().join();

        //delete ONLY the base out-of-band, leaving the section row orphaned
        PlayerController.get().baseManager().deleteAndEvict(uuid).join();

        long removed = PlayerController.get().reapOrphanSections().join();
        assertEquals(1L, removed, "the reaper must remove exactly the orphaned section row");
        assertFalse(PlayerController.get().getBinding(BalancePDSection.class).getRepository().exists(uuid).join(),
                "the orphaned section row must be gone after a reap");
    }

    // ------------------------------------------------------------------
    // indexed top-N aggregate: querySection without loading everything into cache
    // ------------------------------------------------------------------

    @Test
    void querySectionReturnsTopNOverIndexedFieldWithoutLoadingAll() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_query", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        //10 players, each with a distinct balance; only 3 are online/cached at query time
        long[] balances = {50, 900, 30, 700, 10, 500, 20, 300, 40, 100};
        UUID[] uuids = new UUID[balances.length];
        for (int i = 0; i < balances.length; i++) {
            uuids[i] = UUID.randomUUID();
            PlayerController.handleLogin(uuids[i], "P" + i).join();
            BalancePDSection s = PlayerController.getLoaded(uuids[i]).getPDSection(BalancePDSection.class).join();
            s.balance = balances[i];
            s.markDirty();
        }
        PlayerController.get().flushAll().join();

        //clear the section cache so the query cannot be served from memory
        PlayerController.clearPDSections(BalancePDSection.class);
        int cachedBefore = PlayerController.get().getBinding(BalancePDSection.class).getManager().cachedSize();
        assertEquals(0, cachedBefore, "the section cache must be empty before the aggregate query");

        //top-3 by balance descending, indexed field, limit 3 - one backend query, no cache load
        QueryOptions top3 = QueryOptions.builder().descending("balance").limit(3).build();
        List<BalancePDSection> top = PlayerController.get()
                .querySection(BalancePDSection.class, Query.all(), top3).join();

        assertEquals(3, top.size(), "the query must return exactly the top-N");
        assertEquals(900L, top.get(0).balance);
        assertEquals(700L, top.get(1).balance);
        assertEquals(500L, top.get(2).balance);

        int cachedAfter = PlayerController.get().getBinding(BalancePDSection.class).getManager().cachedSize();
        assertEquals(0, cachedAfter, "an indexed query must NOT load the whole collection into cache");
    }

    // ------------------------------------------------------------------
    // schema version present on a persisted entity + upcast hook runs on read
    // ------------------------------------------------------------------

    @Test
    void schemaVersionPersistedAndUpcastRunsOnRead() throws IOException {
        //register a migration that bumps BalancePDSection from v1 to v2, stamping a note (raw-node step)
        EntitySchemaMigrations.register(BalancePDSection.class, 1, node -> node.put("note", "upcast-v2"));
        assertEquals(2, EntitySchemaMigrations.currentVersion(BalancePDSection.class));

        File storageYml = writeH2StorageYml("d_schema", "");
        PlayerController.initialize(storageYml);
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Legacy").join();

        //write a stale v1 row directly to the backend (as an old build would have)
        BalancePDSection old = new BalancePDSection();
        old.uuid = uuid;
        old.balance = 5L;
        old.setSchemaVersion(1);
        PlayerController.get().getBinding(BalancePDSection.class).getRepository().save(old).join();

        //a fresh instance reads it: the upcast hook runs on read
        PlayerController.clearPDSections(BalancePDSection.class);
        BalancePDSection loaded = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        assertEquals(2, loaded.getSchemaVersion(), "the stale payload must be upcast to the current version on read");
        assertEquals("upcast-v2", loaded.note, "the migration step must have run");
        assertTrue(loaded.isDirty(), "an actual upcast must mark the section dirty for re-persist");

        //re-persist and confirm the version is stored as 2
        PlayerController.get().flushAll().join();
        PlayerController.clearPDSections(BalancePDSection.class);
        BalancePDSection persisted = PlayerController.get().getBinding(BalancePDSection.class)
                .getRepository().find(uuid).join().orElseThrow();
        assertEquals(2, persisted.getSchemaVersion(), "the upcast version must be persisted");
    }

    // ------------------------------------------------------------------
    // presence with the DEFAULT hotLoad(true): a login-seeded default is still "absent" until dirtied
    // ------------------------------------------------------------------

    @Test
    void presenceWithDefaultHotLoad_seededDefaultReportsAbsentUntilDirtied() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_presence_hot", ""));
        //default hotLoad(true): login auto-seeds a transient default into the cache
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Hot").join();
        PlayerData playerData = PlayerController.getLoaded(uuid);

        //the hot-load left a cached default (peek is non-null) but no stored row exists yet
        assertNotNull(playerData.getPDSectionIfLoaded(BalancePDSection.class),
                "hotLoad(true) seeds a cached default the sync peek returns");
        assertFalse(playerData.hasPDSection(BalancePDSection.class).join(),
                "a never-dirtied seeded default must report absent");
        assertFalse(playerData.getPDSectionIfPresent(BalancePDSection.class).join().isPresent(),
                "getPDSectionIfPresent must be empty for a transient default");
        assertFalse(PlayerController.get().getBinding(BalancePDSection.class).getRepository().exists(uuid).join(),
                "no row must exist while the default is transient");

        //dirty + flush: now it is a real stored row and both presence primitives report present
        BalancePDSection section = playerData.getPDSection(BalancePDSection.class).join();
        section.balance = 10L;
        section.markDirty();
        PlayerController.get().flushAll().join();

        assertTrue(playerData.hasPDSection(BalancePDSection.class).join(),
                "after markDirty + flush the section is present");
        assertTrue(playerData.getPDSectionIfPresent(BalancePDSection.class).join().isPresent(),
                "getPDSectionIfPresent returns the value once it is persisted");
    }

    // ------------------------------------------------------------------
    // a section written by a NEWER schema than the code is refused by the flush (row untouched)
    // ------------------------------------------------------------------

    @Test
    void aheadSchemaSectionIsRefusedByFlush_rowNotWritten() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_ahead", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "FromTheFuture").join();

        BalancePDSection section = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        section.balance = 500L;
        //stamp a version AHEAD of the code (no migrations registered -> currentVersion == 1)
        section.setSchemaVersion(EntitySchemaMigrations.currentVersion(BalancePDSection.class) + 1);
        section.markDirty();

        //flush must REFUSE the write (would strip newer fields) - no exception, section stays dirty
        PlayerController.get().flushAll().join();

        assertFalse(PlayerController.get().getBinding(BalancePDSection.class).getRepository().exists(uuid).join(),
                "an ahead-of-code schema entity must NOT be written to the backend");
        assertTrue(section.isDirty(), "the refused entity stays dirty (read-only until the code catches up)");
    }

    // ------------------------------------------------------------------
    // eager schema sweep: whole-collection boot migration
    // ------------------------------------------------------------------

    /** Seeds {@code count} stale v1 rows directly on the backend (no cache), returns their uuids. */
    private List<UUID> seedStaleV1Rows(PDSectionBinding<BalancePDSection> binding, int count) {
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID u = UUID.randomUUID();
            uuids.add(u);
            BalancePDSection s = new BalancePDSection();
            s.uuid = u;
            s.balance = i;
            s.setSchemaVersion(1); // written as an old build would have
            binding.getRepository().save(s).join();
        }
        return uuids;
    }

    private EntitySchemaSweeper.SweepReport runSweep(PDSectionBinding<BalancePDSection> binding) {
        // defaults() is what this used to spell out: a random runner id, batches of 256, no abort
        // hook and no logging
        return EntitySchemaSweeper.sweep(binding.getManager(), SweepOptions.defaults());
    }

    @Test
    void eagerSweepMigratesEveryStoredRow_andReRunIsOThe1() throws IOException {
        EntitySchemaMigrations.register(BalancePDSection.class, 1, EntitySchemaMigrationMode.EAGER, node -> node.put("note", "swept-v2"));
        PlayerController.initialize(writeH2StorageYml("d_eager", "schema:\n  eager-sweep-enabled: false"));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        PDSectionBinding<BalancePDSection> binding = PlayerController.get().getBinding(BalancePDSection.class);
        List<UUID> uuids = seedStaleV1Rows(binding, 5);

        EntitySchemaSweeper.SweepReport report = runSweep(binding);
        assertTrue(report.markerAdvanced(), "the sweep must complete and advance the marker");
        assertEquals(5, report.rewritten(), "all five stale rows must be rewritten");

        for (UUID u : uuids) {
            BalancePDSection persisted = binding.getRepository().find(u).join().orElseThrow();
            assertEquals(2, persisted.getSchemaVersion(), "every row must be at v2 after the sweep");
            assertEquals("swept-v2", persisted.note, "the eager migration must have run on every row");
        }

        //re-run: the completion marker turns it into an O(1) skip (no re-scan, no rewrites)
        EntitySchemaSweeper.SweepReport again = runSweep(binding);
        assertFalse(again.markerAdvanced());
        assertTrue(again.note().startsWith("already"), "a completed collection must be an O(1) skip");
    }

    @Test
    void interruptedSweepResumesAndIsIdempotent() throws IOException {
        EntitySchemaMigrations.register(BalancePDSection.class, 1, EntitySchemaMigrationMode.EAGER, node -> node.put("note", "swept-v2"));
        PlayerController.initialize(writeH2StorageYml("d_eager_resume", "schema:\n  eager-sweep-enabled: false"));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());
        PDSectionBinding<BalancePDSection> binding = PlayerController.get().getBinding(BalancePDSection.class);
        UUID u = seedStaleV1Rows(binding, 1).get(0);

        EagerSweepEngine engine = PlayerController.get().sweepEngine();
        runSweep(binding); // first full sweep -> row is v2, marker complete

        //simulate a crash mid-sweep: reset the marker to in-progress with an expired lease
        Repository<String, EntitySchemaSweepMarker> markerRepo = engine.markerRepository(binding.getStorage());
        EntitySchemaSweepMarker marker = markerRepo.find(binding.getCollection()).join().orElseThrow();
        marker.setCompletedVersion(1);
        marker.setInProgressVersion(2);
        marker.setLeaseExpiresAtEpochMs(0L);
        markerRepo.save(marker).join();

        //re-run: it resumes; the already-migrated row decodes clean (no step re-applied), marker re-advances
        EntitySchemaSweeper.SweepReport resume = runSweep(binding);
        assertTrue(resume.markerAdvanced(), "the resumed sweep completes");
        assertEquals(0, resume.rewritten(), "an already-migrated row is never rewritten again (idempotent)");
        assertEquals("swept-v2", binding.getRepository().find(u).join().orElseThrow().note);
    }

    @Test
    void markerIsHint_deletingItReVerifiesWithoutRewriting() throws IOException {
        EntitySchemaMigrations.register(BalancePDSection.class, 1, EntitySchemaMigrationMode.EAGER, node -> node.put("note", "swept-v2"));
        PlayerController.initialize(writeH2StorageYml("d_eager_hint", "schema:\n  eager-sweep-enabled: false"));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());
        PDSectionBinding<BalancePDSection> binding = PlayerController.get().getBinding(BalancePDSection.class);
        seedStaleV1Rows(binding, 3);

        EagerSweepEngine engine = PlayerController.get().sweepEngine();
        runSweep(binding);

        //the marker is only a hint: deleting it must re-verify (scan finds everything current, 0 rewrites)
        engine.markerRepository(binding.getStorage()).delete(binding.getCollection()).join();
        EntitySchemaSweeper.SweepReport reverify = runSweep(binding);
        assertTrue(reverify.markerAdvanced(), "a re-verify completes and re-advances the marker");
        assertEquals(0, reverify.rewritten(), "everything is already current - a re-verify writes nothing");
    }

    @Test
    void updateOnlyWriteNeverResurrectsADeletedRow() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_update_only", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());
        PDSectionBinding<BalancePDSection> binding = PlayerController.get().getBinding(BalancePDSection.class);
        UUID u = UUID.randomUUID();
        BalancePDSection s = new BalancePDSection();
        s.uuid = u;
        s.balance = 9;
        binding.getRepository().save(s).join();
        assertTrue(binding.getRepository().exists(u).join());

        //the row is deleted (as an account roll-forward / deletePlayerData would) between a sweep's read
        //and its write; the sweep's UPDATE_ONLY write must NOT resurrect it
        binding.getRepository().delete(u).join();
        binding.getRepository().saveAll(Collections.singletonList(s), WriteMode.UPDATE_ONLY).join();
        assertFalse(binding.getRepository().exists(u).join(),
                "UPDATE_ONLY must never resurrect a concurrently deleted row");
    }

    // ------------------------------------------------------------------
    // deletePlayerData refuses to delete an ONLINE player's data
    // ------------------------------------------------------------------

    @Test
    void deletePlayerDataRefusedWhileOnline() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_delete_online", ""));

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Online").join();
        playerData.setPlayer(fakeOnlinePlayer(uuid)); //isPlayerOnline() == true now
        assertTrue(playerData.isPlayerOnline());

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> PlayerController.deletePlayerData(uuid).join());
        assertTrue(thrown.getCause() instanceof IllegalStateException,
                "deleting an online player's data must fail with IllegalStateException, was " + thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("ONLINE"), thrown.getCause().getMessage());

        //the base row must still be there (nothing was deleted)
        assertTrue(PlayerController.get().baseManager().repository().exists(uuid).join(),
                "the online guard must have prevented any deletion");
    }

    // ------------------------------------------------------------------
    // static getPDSection lazy-loads the PLAYER (not just the section) from the backend
    // ------------------------------------------------------------------

    @Test
    void staticGetPDSectionLazyLoadsThePlayerFromBackend() throws IOException {
        String db = "d_static_lazy";
        File allYml = writeH2StorageYml(db, "playerdata:\n  load-mode: ALL");
        PlayerController.initialize(allYml);
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerData sleeper = PlayerController.handleLogin(uuid, "Sleeper").join();
        BalancePDSection created = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        created.balance = 321L;
        created.markDirty();
        //age the player far past the RECENT window so the reboot does not eager-load it (offline: the
        //flush keeps this lastSeen instead of materializing 'now')
        sleeper.lastSeen = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(120);
        sleeper.markDirty();
        PlayerController.get().flushAll().join();
        PlayerController.shutdown();

        //reboot in RECENT mode with a short window so the aged player is NOT eager-loaded
        File recentYml = writeH2StorageYml(db, "playerdata:\n  load-mode: RECENT\n  recent-days: 1");
        PlayerController.initialize(recentYml);
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());
        assertFalse(PlayerController.getAllLoaded().stream().anyMatch(p -> p.getUniqueId().equals(uuid)),
                "the old player must not be eager-loaded on RECENT");

        //the static accessor must lazy-load the player AND its section, not return null
        BalancePDSection lazy = PlayerController.getPDSection(uuid, BalancePDSection.class).join();
        assertNotNull(lazy, "static getPDSection must lazy-load a stored player, not return null");
        assertEquals(321L, lazy.balance, "the lazy-loaded section carries the stored data");
    }

    // ------------------------------------------------------------------
    // querySection on a NON-indexed field completes exceptionally (contract)
    // ------------------------------------------------------------------

    @Test
    void querySectionOnNonIndexedFieldFailsExceptionally() throws IOException {
        PlayerController.initialize(writeH2StorageYml("d_query_nonindexed", ""));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, BalancePDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Q").join();
        BalancePDSection s = PlayerController.getLoaded(uuid).getPDSection(BalancePDSection.class).join();
        s.note = "hello"; //'note' is NOT @Indexed
        s.markDirty();
        PlayerController.get().flushAll().join();

        //ordering by a non-indexed field is a contract violation the backend rejects (the backend may
        //reject it synchronously or through the returned future - both are the same contract breach)
        QueryOptions byNote = QueryOptions.builder().ascending("note").build();
        Throwable thrown = assertThrows(Throwable.class,
                () -> PlayerController.get().querySection(BalancePDSection.class, Query.all(), byNote).join());
        Throwable cause = thrown instanceof CompletionException && thrown.getCause() != null
                ? thrown.getCause() : thrown;
        assertTrue(cause instanceof IllegalArgumentException,
                "a non-indexed query must fail with IllegalArgumentException, was " + cause);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A minimal FPlayer whose {@code isOnline()} returns true, for the online-delete guard. */
    private static FPlayer fakeOnlinePlayer(UUID uuid) {
        return (FPlayer) Proxy.newProxyInstance(
                FPlayer.class.getClassLoader(), new Class<?>[]{FPlayer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOnline":
                            return true;
                        case "getUniqueId":
                            return uuid;
                        case "getName":
                            return "Online";
                        case "toString":
                            return "FakeOnlinePlayer[" + uuid + "]";
                        case "hashCode":
                            return uuid.hashCode();
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }
}
