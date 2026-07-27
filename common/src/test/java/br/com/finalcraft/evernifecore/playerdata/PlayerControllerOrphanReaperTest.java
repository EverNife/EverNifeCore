package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orphan reaper: a section row survives exactly as long as its base PlayerData does, no matter how
 * many pages the collection spans, and a row whose payload no longer decodes neither stops the sweep nor
 * gets reaped on the strength of a payload nobody could read. Runs on H2 mem - no Docker.
 *
 * <p>Sections are seeded straight onto the repository (no login), so "has a base" is set per uuid rather
 * than implied by the fixture - which is what lets a single collection carry orphans and non-orphans
 * side by side.</p>
 */
@ECoreTest
class PlayerControllerOrphanReaperTest {

    /** Rows per reaper page - mirrors the production constant, so the tests can straddle a page edge. */
    private static final int PAGE = 256;


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class LootPDSection extends PDSection {
        public long amount;
    }

    // ------------------------------------------------------------------
    // fixture
    // ------------------------------------------------------------------

    private String url;


    /** Boots the controller with {@code LootPDSection} registered, and returns its binding. */
    private PDSectionBinding<LootPDSection> boot(String dbName) throws IOException {
        Storages storages = Storages.h2(dbName);
        url = storages.jdbcUrl();
        PlayerController.initialize(storages.writeTo(tempDir));
        //hotLoad(false): a login must not auto-seed a section, so each uuid gets exactly the rows this
        //test asks for
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, LootPDSection.class).hotLoad(false).build());
        return PlayerController.get().getBinding(LootPDSection.class);
    }

    /** Persists a section row for {@code uuid} without going through the cache. */
    private void seedSection(PDSectionBinding<LootPDSection> binding, UUID uuid, long amount) {
        LootPDSection section = new LootPDSection();
        section.uuid = uuid;
        section.amount = amount;
        binding.getRepository().save(section).join();
    }

    /** Persists a base PlayerData for {@code uuid}, so a section row of that uuid is NOT an orphan. */
    private void seedBase(UUID uuid, String name) {
        PlayerController.handleLogin(uuid, name).join();
        PlayerController.get().flushAll().join();
    }

    private boolean sectionExists(PDSectionBinding<LootPDSection> binding, UUID uuid) {
        return binding.getRepository().exists(uuid).join();
    }

    /** Overwrites a row's stored payload with bytes the codec cannot decode back into an entity. */
    private void poisonPayload(PDSectionBinding<LootPDSection> binding, UUID uuid) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE \"" + binding.getCollection() + "\" SET \"storage_data\" = ? WHERE \"storage_key\" = ?")) {
            ps.setString(1, "}}} this was a payload once {{{");
            ps.setString(2, uuid.toString());
            assertEquals(1, ps.executeUpdate(), "the row to poison must exist");
        }
    }

    // ------------------------------------------------------------------
    // an orphan goes, a live section stays - side by side in one collection
    // ------------------------------------------------------------------

    @Test
    void reapRemovesTheOrphanAndKeepsTheSectionWhoseBaseIsAlive() throws IOException {
        PDSectionBinding<LootPDSection> binding = boot("d_reap_mixed");

        UUID alive = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        seedBase(alive, "Alive");
        seedSection(binding, alive, 10L);
        seedSection(binding, orphan, 20L); //no base was ever created for this one

        long removed = PlayerController.get().reapOrphanSections().join();

        assertEquals(1L, removed, "only the section with no base may be reaped");
        assertTrue(sectionExists(binding, alive), "a section whose base is alive must survive the reap");
        assertFalse(sectionExists(binding, orphan), "a section with no base must be reaped");
    }

    // ------------------------------------------------------------------
    // nothing to reap: a collection where every section has a base is untouched
    // ------------------------------------------------------------------

    @Test
    void reapIsANoOpWhenEverySectionHasABase() throws IOException {
        PDSectionBinding<LootPDSection> binding = boot("d_reap_noop");

        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID uuid = UUID.randomUUID();
            uuids.add(uuid);
            seedBase(uuid, "Keep" + i);
            seedSection(binding, uuid, i);
        }

        assertEquals(0L, PlayerController.get().reapOrphanSections().join(),
                "a collection with no orphan must report nothing reaped");
        for (UUID uuid : uuids) {
            assertTrue(sectionExists(binding, uuid), "no live section may be touched by a reap");
        }
    }

    // ------------------------------------------------------------------
    // the scan spans several pages: every row is judged, none twice, none skipped
    // ------------------------------------------------------------------

    @Test
    void reapWalksEveryPageOfACollectionLargerThanOnePage() throws IOException {
        PDSectionBinding<LootPDSection> binding = boot("d_reap_paged");

        //600 rows over pages of 256 -> three pages (256 + 256 + 88)
        int total = (PAGE * 2) + 88;
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < total; i++) uuids.add(UUID.randomUUID());
        //the scan is key-ordered, so sorting by the stored key form lets the survivors be aimed at the
        //page edges - where an off-by-one in the cursor would skip or re-visit a row
        uuids.sort(Comparator.comparing(UUID::toString));

        Set<UUID> keepAlive = new HashSet<>();
        for (int index : Arrays.asList(0, 1, PAGE - 1, PAGE, PAGE + 1, (PAGE * 2) - 1, PAGE * 2, total - 1)) {
            keepAlive.add(uuids.get(index));
        }
        for (UUID uuid : keepAlive) seedBase(uuid, "Survivor");
        for (int i = 0; i < total; i++) seedSection(binding, uuids.get(i), i);

        long removed = PlayerController.get().reapOrphanSections().join();

        assertEquals(total - keepAlive.size(), removed,
                "every orphan across every page must be reaped exactly once");
        for (UUID uuid : uuids) {
            assertEquals(keepAlive.contains(uuid), sectionExists(binding, uuid),
                    "section " + uuid + " survives iff its base does");
        }
    }

    // ------------------------------------------------------------------
    // a poisoned row: the sweep carries on, and the row it cannot read is left alone
    // ------------------------------------------------------------------

    @Test
    void undecodableRowNeitherStopsTheReapNorGetsReaped() throws IOException, SQLException {
        PDSectionBinding<LootPDSection> binding = boot("d_reap_poison");

        UUID alive = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        UUID poisoned = UUID.randomUUID();
        seedBase(alive, "Alive");
        seedSection(binding, alive, 10L);
        seedSection(binding, orphan, 20L);
        seedSection(binding, poisoned, 30L);
        //the poisoned row has NO base either: were it readable it would be reaped as an orphan, so
        //leaving it is the payload being unreadable, not the row looking alive
        poisonPayload(binding, poisoned);

        long removed = PlayerController.get().reapOrphanSections().join();

        assertEquals(1L, removed, "the reap must run to completion and reap the readable orphan");
        assertFalse(sectionExists(binding, orphan), "the readable orphan must still be reaped");
        assertTrue(sectionExists(binding, poisoned),
                "a row whose payload does not decode must be left for an admin, never deleted on a guess");
        assertTrue(sectionExists(binding, alive), "the live section is untouched");
    }

    // ------------------------------------------------------------------
    // a base live in cache but not yet flushed still shields its section
    // ------------------------------------------------------------------

    @Test
    void sectionSurvivesWhenItsBaseIsCacheResidentButNotYetInTheBackend() throws IOException {
        PDSectionBinding<LootPDSection> binding = boot("d_reap_cached_base");

        UUID cachedBase = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        //the section rows live in the backend, so the scan sees them both
        seedSection(binding, cachedBase, 10L);
        seedSection(binding, orphan, 20L);
        //cachedBase's base is present only in the cache (seedIfAbsent does no write): the backend
        //version scan will not see it, so the reaper must fall back to the cache before deleting
        PlayerController.get().baseManager()
                .seedIfAbsent(cachedBase, new PlayerData(cachedBase, "Cached"));

        long removed = PlayerController.get().reapOrphanSections().join();

        assertEquals(1L, removed, "only the section with no base anywhere may be reaped");
        assertTrue(sectionExists(binding, cachedBase),
                "a section whose base is live in cache must survive even though the backend has no base row");
        assertFalse(sectionExists(binding, orphan), "a section with no base at all must be reaped");
    }

    // ------------------------------------------------------------------
    // a poisoned row on its own page does not truncate the scan
    // ------------------------------------------------------------------

    @Test
    void reapContinuesPastAPoisonedRowIntoLaterPages() throws IOException, SQLException {
        PDSectionBinding<LootPDSection> binding = boot("d_reap_poison_paged");

        int total = PAGE + 20; //two pages
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < total; i++) uuids.add(UUID.randomUUID());
        uuids.sort(Comparator.comparing(UUID::toString));
        for (int i = 0; i < total; i++) seedSection(binding, uuids.get(i), i);

        //poison a row on the FIRST page: the orphans behind it, on the second page, must still be reaped
        UUID poisoned = uuids.get(0);
        poisonPayload(binding, poisoned);

        long removed = PlayerController.get().reapOrphanSections().join();

        assertEquals(total - 1, removed, "a poisoned row must not truncate the scan at its page");
        assertTrue(sectionExists(binding, poisoned), "the poisoned row itself is left alone");
        for (int i = 1; i < total; i++) {
            assertFalse(sectionExists(binding, uuids.get(i)), "every other orphan must be gone");
        }
    }
}
