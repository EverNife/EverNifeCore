package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AccountSection behaviour: keying by the stamped accountId (== uuid unlinked, canonical id for a
 * linked member), the shared-row round-trip, refresh on login, merge-based conflict resolution,
 * presence semantics of the transient default, and the delete rule (a singleton's rows cascade;
 * a linked account's rows survive one member's deletion).
 */
@ECoreTest
class AccountSectionTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    /** Network-wide achievements: merge = set union (associative, commutative, idempotent). */
    public static class AchievementsSection extends AccountSection<AchievementsSection> {
        public Set<String> unlocked = new LinkedHashSet<>();

        @Override
        public AchievementsSection merge(List<AchievementsSection> others) {
            AchievementsSection merged = new AchievementsSection();
            merged.unlocked.addAll(this.unlocked);
            for (AchievementsSection other : others) {
                merged.unlocked.addAll(other.unlocked);
            }
            return merged;
        }
    }

    private File writeStorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "network:",
                "  storage-backend-id: test_h2",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void registerAchievements() {
        PlayerController.registerAccountSectionCfg(
                AccountSectionConfiguration.builder(null, AchievementsSection.class, "achievements").build());
    }

    /** Persists a canonical account with {@code memberUuid} linked in, plus the member's alias row. */
    private UUID persistLinkedAccount(UUID memberUuid, String memberName) {
        UUID canonicalId = UUID.randomUUID();
        Account canonical = Account.singleton(canonicalId, Accounts.PLATFORM_PROVIDER,
                canonicalId.toString(), "Owner");
        canonical.addMember(new AccountMember(Accounts.PLATFORM_PROVIDER, memberUuid.toString(), memberName));
        Accounts.get().getManager().saveAndCache(canonical).join();
        Accounts.get().getManager().saveAndCache(Account.alias(memberUuid, canonicalId)).join();
        return canonicalId;
    }

    // ------------------------------------------------------------------
    // keying + round-trip
    // ------------------------------------------------------------------

    @Test
    void unlinkedPlayer_sectionKeysByUuid_andRoundTrips() throws IOException {
        File storageYml = writeStorageYml("acs_unlinked");
        PlayerController.initialize(storageYml);
        registerAchievements();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Solo").join();
        AchievementsSection section = playerData.getAccountSection(AchievementsSection.class).join();
        assertEquals(uuid, section.getAccountId(), "an unlinked player's account row keys by the uuid");

        section.unlocked.add("first_join");
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.initialize(storageYml);
        registerAchievements();
        AchievementsSection reloaded = PlayerController
                .getAccountSectionByAccountId(uuid, AchievementsSection.class).join();
        assertTrue(reloaded.unlocked.contains("first_join"), "the account row round-trips under the uuid key");
    }

    // ------------------------------------------------------------------
    // re-registration reloads the account section (the PDSection mirror)
    // ------------------------------------------------------------------

    @Test
    void reRegisteringFlushesThenDropsTheCachedRows() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_reload"));
        registerAchievements();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Reloader").join();
        AchievementsSection before = playerData.getAccountSection(AchievementsSection.class).join();
        before.unlocked.add("unflushed");
        before.markDirty();

        registerAchievements(); //the plugin reload re-registers

        assertNull(PlayerController.getLoadedAccountSection(uuid, AchievementsSection.class),
                "the previous session's row must be dropped");
        AchievementsSection after = PlayerController
                .getAccountSectionByAccountId(uuid, AchievementsSection.class).join();
        assertTrue(after.unlocked.contains("unflushed"),
                "the unflushed row must have been persisted before the cache was dropped");
    }

    @Test
    void reRegisteringDiscardsUnflushedRowsWhenTheSectionAsksForIt() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_reload_discard"));
        PlayerController.registerAccountSectionCfg(AccountSectionConfiguration
                .builder(null, AchievementsSection.class, "achievements").discardDirtyOnReload().build());

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Derived").join();
        AchievementsSection section = playerData.getAccountSection(AchievementsSection.class).join();
        section.unlocked.add("derived");
        section.markDirty();

        PlayerController.registerAccountSectionCfg(AccountSectionConfiguration
                .builder(null, AchievementsSection.class, "achievements").discardDirtyOnReload().build());

        AchievementsSection after = PlayerController
                .getAccountSectionByAccountId(uuid, AchievementsSection.class).join();
        assertFalse(after.unlocked.contains("derived"),
                "discardDirtyOnReload must drop the unflushed row instead of persisting it");
    }

    @Test
    void linkedMember_sectionKeysByCanonicalId_andIsSharedAcrossMembers() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_linked"));
        registerAchievements();

        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();
        UUID canonicalId = persistLinkedAccount(memberA, "MainName");
        Account canonical = Accounts.get().account(canonicalId).join();
        canonical.addMember(new AccountMember(Accounts.PLATFORM_PROVIDER, memberB.toString(), "AltName"));
        Accounts.get().getManager().saveAndCache(canonical).join();
        Accounts.get().getManager().saveAndCache(Account.alias(memberB, canonicalId)).join();

        PlayerData playerA = PlayerController.handleLogin(memberA, "MainName").join();
        AchievementsSection viaA = playerA.getAccountSection(AchievementsSection.class).join();
        assertEquals(canonicalId, viaA.getAccountId(), "a linked member's account row keys by the canonical id");

        viaA.unlocked.add("dragon_slayer");
        viaA.markDirty();
        PlayerController.get().flushAll().join();

        PlayerData playerB = PlayerController.handleLogin(memberB, "AltName").join();
        AchievementsSection viaB = playerB.getAccountSection(AchievementsSection.class).join();
        assertTrue(viaB.unlocked.contains("dragon_slayer"),
                "both linked members read the SAME account row");
    }

    // ------------------------------------------------------------------
    // refresh on login: data written by another instance becomes visible
    // ------------------------------------------------------------------

    @Test
    void loginRefreshesIdleCachedRowFromBackend() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_refresh"));
        registerAchievements();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Hopper").join();
        AchievementsSection section = playerData.getAccountSection(AchievementsSection.class).join();
        section.unlocked.add("local_one");
        section.markDirty();
        PlayerController.get().flushAll().join();

        //another instance of the network writes the same row directly in the backend
        AccountSectionBinding<AchievementsSection> binding =
                PlayerController.get().accountEngine().getBinding(AchievementsSection.class);
        AchievementsSection remote = binding.getRepository().find(uuid).join().get();
        remote.unlocked.add("remote_two");
        binding.getRepository().save(remote).join();

        //no active local session uses the row: the next login re-reads it
        PlayerData again = PlayerController.handleLogin(uuid, "Hopper").join();
        AchievementsSection refreshed = again.getAccountSection(AchievementsSection.class).join();
        assertTrue(refreshed.unlocked.contains("remote_two"),
                "a login must surface data written by another instance");
        assertTrue(refreshed.unlocked.contains("local_one"));
    }

    // ------------------------------------------------------------------
    // conflict resolution = merge (no side is dropped). The generic conflict pipeline is covered by
    // PlayerControllerConflictPipelineTest; here the account-specific resolution step is exercised
    // directly (a real cross-instance conflict needs a lock-enforcing backend - the manual suite).
    // ------------------------------------------------------------------

    @Test
    void mergeStoredStateCombinesBothSidesAndKeepsFrameworkIdentity() {
        UUID accountKey = UUID.randomUUID();

        AchievementsSection live = new AchievementsSection();
        live.attachAccountId(accountKey);
        live.unlocked.add("local_win");
        live.recordMergedKey(UUID.randomUUID(), 3L);

        AchievementsSection storedWinner = new AchievementsSection();
        storedWinner.attachAccountId(accountKey);
        storedWinner.unlocked.add("remote_win");
        storedWinner.lockVersion = 9L;
        UUID sharedLedgerKey = UUID.randomUUID();
        storedWinner.recordMergedKey(sharedLedgerKey, 7L);

        live.mergeStoredState(storedWinner);

        assertTrue(live.unlocked.containsAll(Arrays.asList("local_win", "remote_win")),
                "the resolution must keep BOTH sides: " + live.unlocked);
        assertEquals(accountKey, live.getAccountId(), "the storage key must survive the merge copy");
        assertEquals(Long.valueOf(9L), live.lockVersion, "the winner's lock version is adopted");
        assertTrue(live.isDirty(), "the merged state must be re-persisted");
        assertEquals(2, live.mergedKeys.size(), "the absorption ledgers of both rows are united");
        assertEquals(Long.valueOf(7L), live.findMergedKey(sharedLedgerKey).getLockVersion());
    }

    // ------------------------------------------------------------------
    // presence semantics + merge purity
    // ------------------------------------------------------------------

    @Test
    void transientDefaultReportsAbsentUntilDirtied() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_presence"));
        registerAchievements();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Ghost").join();
        AchievementsSection section = playerData.getAccountSection(AchievementsSection.class).join();
        assertTrue(section.isTransientDefault(), "a seeded default is cache-only");
        assertFalse(playerData.getAccountSectionIfPresent(AchievementsSection.class).join().isPresent(),
                "the presence primitive must report absence for a never-dirtied default");

        section.unlocked.add("now_real");
        section.markDirty();
        PlayerController.get().flushAll().join();
        assertTrue(playerData.getAccountSectionIfPresent(AchievementsSection.class).join().isPresent(),
                "once dirtied+persisted the row is present");
    }

    @Test
    void mergeIsPure_returnsNewInstanceAndDoesNotMutateInputs() {
        AchievementsSection base = new AchievementsSection();
        base.unlocked.add("a");
        AchievementsSection other = new AchievementsSection();
        other.unlocked.add("b");

        AchievementsSection merged = base.merge(new ArrayList<>(Arrays.asList(other)));
        assertNotSame(base, merged);
        assertNotSame(other, merged);
        assertTrue(merged.unlocked.containsAll(Arrays.asList("a", "b")));
        assertEquals(1, base.unlocked.size(), "the receiver must not be mutated");
        assertEquals(1, other.unlocked.size(), "an input must not be mutated");
    }

    // ------------------------------------------------------------------
    // deletePlayerData: singleton cascades, linked account survives
    // ------------------------------------------------------------------

    @Test
    void deleteSingletonPlayerRemovesItsAccountRow() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_delete_solo"));
        registerAchievements();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Deleted").join();
        AchievementsSection section = playerData.getAccountSection(AchievementsSection.class).join();
        section.unlocked.add("gone_soon");
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.deletePlayerData(uuid).join();

        AccountSectionBinding<AchievementsSection> binding =
                PlayerController.get().accountEngine().getBinding(AchievementsSection.class);
        assertFalse(binding.getRepository().find(uuid).join().isPresent(),
                "a singleton player's account row cascades with the delete");
        assertNull(PlayerController.getLoaded(uuid));
    }

    @Test
    void deleteLinkedMemberKeepsTheSharedAccountRow() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_delete_linked"));
        registerAchievements();

        UUID memberUuid = UUID.randomUUID();
        UUID canonicalId = persistLinkedAccount(memberUuid, "Alt");

        PlayerData member = PlayerController.handleLogin(memberUuid, "Alt").join();
        AchievementsSection section = member.getAccountSection(AchievementsSection.class).join();
        section.unlocked.add("shared_data");
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.deletePlayerData(memberUuid).join();

        AccountSectionBinding<AchievementsSection> binding =
                PlayerController.get().accountEngine().getBinding(AchievementsSection.class);
        assertTrue(binding.getRepository().find(canonicalId).join().isPresent(),
                "a linked account's shared row must survive one member's deletion");
        assertNull(PlayerController.getLoaded(memberUuid), "the member's base entity is gone");
    }

    @Test
    void deleteLinkedMemberDropsItsOfflineFormerKeyRow() throws IOException {
        PlayerController.initialize(writeStorageYml("acs_delete_linked_offline"));
        registerAchievements();

        //the member logged in and wrote data as a SINGLETON: the account row keys by its own uuid
        UUID memberUuid = UUID.randomUUID();
        PlayerData member = PlayerController.handleLogin(memberUuid, "Solo").join();
        AchievementsSection section = member.getAccountSection(AchievementsSection.class).join();
        assertEquals(memberUuid, section.getAccountId(), "as a singleton the row keys by the uuid");
        section.unlocked.add("pre_link_data");
        section.markDirty();
        PlayerController.get().flushAll().join();

        //it is now linked into a canonical account but never logs in again, so the former-key row under
        //its own uuid is never absorbed (absorption only runs at login via migrateAndStamp)
        persistLinkedAccount(memberUuid, "Solo");

        PlayerController.deletePlayerData(memberUuid).join();

        AccountSectionBinding<AchievementsSection> binding =
                PlayerController.get().accountEngine().getBinding(AchievementsSection.class);
        assertFalse(binding.getRepository().find(memberUuid).join().isPresent(),
                "the linked member's former-key account row must be removed, not left orphaned");
        assertNull(PlayerController.getLoaded(memberUuid));
    }
}
