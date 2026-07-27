package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The linking flows of the account layer: an external identity birthing an explicit account,
 * transitive fusion through a shared external identity, the strict desiredAccountId rules, the
 * lazy roll-forward data migration at login (including the ledger's crash-resume and stale-session
 * re-absorption for a NON-idempotent merge), unlink semantics (the member starts fresh, the account
 * keeps the data) and the forced offline reconciliation.
 */
@ECoreTest
class AccountsLinkTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    /** Network-wide kill counter: merge = SUM - deliberately NON-idempotent (the ledger's target). */
    public static class KillCountSection extends AccountSection<KillCountSection> {
        public long kills = 0;

        @Override
        public KillCountSection merge(List<KillCountSection> others) {
            KillCountSection merged = new KillCountSection();
            merged.kills = this.kills;
            for (KillCountSection other : others) {
                merged.kills += other.kills;
            }
            return merged;
        }
    }

    /** Network-wide achievements: merge = set union. */
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

    private void bootstrapEnabled(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "multi-platform-accounts:",
                "  enabled: true",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        PlayerController.initialize(file);
    }

    private void registerSections() {
        PlayerController.registerAccountSectionCfg(
                AccountSectionConfiguration.builder(null, KillCountSection.class).build());
        PlayerController.registerAccountSectionCfg(
                AccountSectionConfiguration.builder(null, AchievementsSection.class).build());
    }

    private long storedKills(UUID accountKey) {
        AccountSectionBinding<KillCountSection> binding =
                PlayerController.get().accountEngine().getBinding(KillCountSection.class);
        Optional<KillCountSection> row = binding.getRepository().find(accountKey).join();
        return row.map(section -> section.kills).orElse(-1L);
    }

    private static Throwable rootCause(CompletionException failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    // ------------------------------------------------------------------
    // linkExternal: birth of the explicit account
    // ------------------------------------------------------------------

    @Test
    void linkExternalBirthsExplicitAccountWithMintedId() throws IOException {
        bootstrapEnabled("link_birth");

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Alpha").join();

        Account account = Accounts.get().linkExternal(uuid, "site", "user-1").join();
        assertNotEquals(uuid, account.getAccountId(), "the explicit account gets its OWN minted id");
        assertFalse(account.isSingleton());
        assertEquals(2, account.getMembers().size(), "platform member + external member");
        assertEquals("Alpha", account.findMember(Accounts.platformProvider(), uuid.toString()).getName());

        Account byExternal = Accounts.get().findByExternal("site", "user-1").join().orElse(null);
        assertEquals(account.getAccountId(), byExternal.getAccountId(),
                "the external identity resolves to the account through its derived alias row");
        assertEquals(account.getAccountId(), Accounts.get().account(uuid).join().getAccountId(),
                "the member uuid resolves to the account through its alias row");

        //idempotent re-link of the same identity
        Account again = Accounts.get().linkExternal(uuid, "site", "user-1").join();
        assertEquals(account.getAccountId(), again.getAccountId());

        PlayerData playerData = PlayerController.handleLogin(uuid, "Alpha").join();
        assertEquals(account.getAccountId(), playerData.getAccountId(),
                "the next login re-stamps the resolved account id");
    }

    @Test
    void linkExternalRejectsReservedAndAmbiguousProviders() throws IOException {
        bootstrapEnabled("link_reject");
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Rules").join();

        CompletionException reserved = assertThrows(CompletionException.class,
                () -> Accounts.get().linkExternal(uuid, Accounts.platformProvider(), "x").join());
        assertTrue(rootCause(reserved).getMessage().contains("reserved"));

        CompletionException ambiguous = assertThrows(CompletionException.class,
                () -> Accounts.get().linkExternal(uuid, "si:te", "x").join());
        assertTrue(rootCause(ambiguous).getMessage().contains("ambiguous"));
    }

    // ------------------------------------------------------------------
    // lazy roll-forward: data follows the link at the next login
    // ------------------------------------------------------------------

    @Test
    void dataFollowsTheLinkAtNextLogin() throws IOException {
        bootstrapEnabled("link_rollforward");
        registerSections();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Mover").join();
        KillCountSection kills = playerData.getAccountSection(KillCountSection.class).join();
        kills.kills = 5;
        kills.markDirty();
        AchievementsSection achievements = playerData.getAccountSection(AchievementsSection.class).join();
        achievements.unlocked.add("first_blood");
        achievements.markDirty();
        PlayerController.get().flushAll().join();

        UUID accountId = Accounts.get().linkExternal(uuid, "site", "mover").join().getAccountId();
        assertEquals(5, storedKills(uuid), "the link itself moves NO data - only identity");

        PlayerData relogged = PlayerController.handleLogin(uuid, "Mover").join();
        assertEquals(accountId, relogged.getAccountId());
        assertEquals(-1, storedKills(uuid), "the old row is deleted after being absorbed");
        assertEquals(5, storedKills(accountId), "the data moved under the canonical key");

        KillCountSection migrated = relogged.getAccountSection(KillCountSection.class).join();
        assertEquals(5, migrated.kills);
        assertTrue(migrated.findMergedKey(uuid) != null, "the absorption is recorded in the ledger");
        AchievementsSection migratedAchievements = relogged.getAccountSection(AchievementsSection.class).join();
        assertTrue(migratedAchievements.unlocked.contains("first_blood"));
    }

    // ------------------------------------------------------------------
    // transitive fusion through a shared external identity
    // ------------------------------------------------------------------

    @Test
    void transitiveFusionJoinsTwoPlayersThroughTheSameExternal() throws IOException {
        bootstrapEnabled("link_transitive");
        registerSections();

        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        PlayerData playerA = PlayerController.handleLogin(uuidA, "Alpha").join();
        PlayerData playerB = PlayerController.handleLogin(uuidB, "Beta").join();

        KillCountSection killsA = playerA.getAccountSection(KillCountSection.class).join();
        killsA.kills = 3;
        killsA.markDirty();
        KillCountSection killsB = playerB.getAccountSection(KillCountSection.class).join();
        killsB.kills = 4;
        killsB.markDirty();
        PlayerController.get().flushAll().join();

        UUID accountId = Accounts.get().linkExternal(uuidA, "site", "hub").join().getAccountId();
        Account fused = Accounts.get().linkExternal(uuidB, "site", "hub").join();
        assertEquals(accountId, fused.getAccountId(),
                "the second link fuses INTO the account already holding the external identity");
        assertEquals(3, fused.getMembers().size(), "two platform members + the external");
        assertEquals(accountId, Accounts.get().account(uuidB).join().getAccountId());

        PlayerController.handleLogin(uuidA, "Alpha").join();
        assertEquals(3, storedKills(accountId), "A's kills absorbed at A's login");
        PlayerController.handleLogin(uuidB, "Beta").join();
        assertEquals(7, storedKills(accountId), "B's kills SUMMED in at B's login");
        assertEquals(-1, storedKills(uuidA));
        assertEquals(-1, storedKills(uuidB));
    }

    // ------------------------------------------------------------------
    // desiredAccountId: strictly validated, creation-only
    // ------------------------------------------------------------------

    @Test
    void desiredAccountIdIsHonoredOnCreationAndIdempotentAfter() throws IOException {
        bootstrapEnabled("desired_ok");

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "SiteUser").join();
        UUID desired = UUID.randomUUID();

        Account account = Accounts.get().linkExternal(uuid, "site", "u9", desired).join();
        assertEquals(desired, account.getAccountId(), "the account is born under the caller's id");

        Account again = Accounts.get().linkExternal(uuid, "site", "u9", desired).join();
        assertEquals(desired, again.getAccountId(), "restating the same id is an idempotent no-op");

        CompletionException rekey = assertThrows(CompletionException.class,
                () -> Accounts.get().linkExternal(uuid, "site2", "other", UUID.randomUUID()).join());
        assertTrue(rootCause(rekey).getMessage().contains("never re-keyed"),
                "a different id against a live account must fail: " + rootCause(rekey).getMessage());
    }

    @Test
    void desiredAccountIdRejectsCollisions() throws IOException {
        bootstrapEnabled("desired_collisions");

        UUID linkedUuid = UUID.randomUUID();
        PlayerController.handleLogin(linkedUuid, "Taken").join();
        UUID takenId = Accounts.get().linkExternal(linkedUuid, "site", "taken").join().getAccountId();

        //the member's own uuid
        UUID uuidB = UUID.randomUUID();
        PlayerController.handleLogin(uuidB, "SelfKey").join();
        CompletionException memberUuid = assertThrows(CompletionException.class,
                () -> Accounts.get().linkExternal(uuidB, "site", "uB", uuidB).join());
        assertTrue(rootCause(memberUuid).getMessage().contains("member"));

        //an id already present in the account collection
        UUID uuidC = UUID.randomUUID();
        PlayerController.handleLogin(uuidC, "CollideAccount").join();
        CompletionException existing = assertThrows(CompletionException.class,
                () -> Accounts.get().linkExternal(uuidC, "site", "uC", takenId).join());
        assertTrue(rootCause(existing).getMessage().contains("already"));

        //the uuid of a stored PlayerData (a site that keys users by their platform uuid)
        UUID storedPlayer = UUID.randomUUID();
        PlayerController.handleLogin(storedPlayer, "Bystander").join();
        UUID uuidD = UUID.randomUUID();
        PlayerController.handleLogin(uuidD, "CollidePlayer").join();
        CompletionException playerBase = assertThrows(CompletionException.class,
                () -> Accounts.get().linkExternal(uuidD, "site", "uD", storedPlayer).join());
        assertTrue(rootCause(playerBase).getMessage().contains("PlayerData"));

        //nothing was written by the failed attempts
        assertFalse(Accounts.get().findByExternal("site", "uB").join().isPresent());
        assertFalse(Accounts.get().findByExternal("site", "uC").join().isPresent());
        assertFalse(Accounts.get().findByExternal("site", "uD").join().isPresent());
    }

    // ------------------------------------------------------------------
    // the ledger: crash-resume and stale-session re-absorption of a NON-idempotent merge
    // ------------------------------------------------------------------

    @Test
    void ledgerMakesInterruptedMigrationSafeForNonIdempotentMerge() throws IOException {
        bootstrapEnabled("ledger_resume");
        registerSections();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Summed").join();
        KillCountSection kills = playerData.getAccountSection(KillCountSection.class).join();
        kills.kills = 10;
        kills.markDirty();
        PlayerController.get().flushAll().join();

        UUID accountId = Accounts.get().linkExternal(uuid, "site", "sum").join().getAccountId();
        PlayerController.handleLogin(uuid, "Summed").join();
        assertEquals(10, storedKills(accountId));

        AccountSectionBinding<KillCountSection> binding =
                PlayerController.get().accountEngine().getBinding(KillCountSection.class);
        KillCountSection target = PlayerController
                .getAccountSectionByAccountId(accountId, KillCountSection.class).join();

        //CRASH-RESUME: the absorbed old row re-appears exactly as recorded (written, delete lost)
        KillCountSection ghost = new KillCountSection();
        ghost.attachAccountId(uuid);
        ghost.kills = 10;
        ghost.lockVersion = target.findMergedKey(uuid).getLockVersion();
        binding.getRepository().save(ghost).join();
        //align the ledger with what the backend actually stored (a lock-managing backend may bump it)
        target.recordMergedKey(uuid, binding.getRepository().find(uuid).join().get().lockVersion);

        PlayerController.get().accountEngine().migrateKeyedRows(uuid, accountId).join();
        assertEquals(-1, storedKills(uuid), "the resumed run finishes the delete");
        assertEquals(10, storedKills(accountId), "the SUM was NOT applied twice");

        //STALE SESSION: the old row was re-written AFTER the absorption (higher lock version)
        KillCountSection stale = new KillCountSection();
        stale.attachAccountId(uuid);
        stale.kills = 2;
        stale.lockVersion = 999L;
        binding.getRepository().save(stale).join();

        PlayerController.get().accountEngine().migrateKeyedRows(uuid, accountId).join();
        assertEquals(-1, storedKills(uuid));
        assertEquals(12, storedKills(accountId), "the late writes are re-absorbed exactly once");
    }

    // ------------------------------------------------------------------
    // unlink: the member starts fresh, the account keeps the shared data
    // ------------------------------------------------------------------

    @Test
    void unlinkedMemberStartsFreshAndAccountKeepsData() throws IOException {
        bootstrapEnabled("unlink_fresh");
        registerSections();

        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        PlayerData playerB = PlayerController.handleLogin(uuidB, "Beta").join();
        PlayerController.handleLogin(uuidA, "Alpha").join();
        KillCountSection killsB = playerB.getAccountSection(KillCountSection.class).join();
        killsB.kills = 7;
        killsB.markDirty();
        PlayerController.get().flushAll().join();

        UUID accountId = Accounts.get().linkExternal(uuidA, "site", "duo").join().getAccountId();
        Accounts.get().linkExternal(uuidB, "site", "duo").join();
        PlayerController.handleLogin(uuidB, "Beta").join();
        assertEquals(7, storedKills(accountId), "B's kills were absorbed into the account");

        Account after = Accounts.get().unlink(uuidB).join();
        assertEquals(accountId, after.getAccountId(), "the account stays explicit");
        assertNull(after.findMember(Accounts.platformProvider(), uuidB.toString()),
                "the member left the account");

        PlayerData reloggedB = PlayerController.handleLogin(uuidB, "Beta").join();
        assertEquals(uuidB, reloggedB.getAccountId(), "the unlinked member stamps back to its own uuid");
        assertEquals(7, storedKills(accountId), "the account KEEPS the shared data after the unlink");
        assertFalse(reloggedB.getAccountSectionIfPresent(KillCountSection.class).join().isPresent(),
                "the member starts FRESH - no row under its own key");

        //a never-linked uuid cannot be unlinked
        CompletionException notLinked = assertThrows(CompletionException.class,
                () -> Accounts.get().unlink(UUID.randomUUID()).join());
        assertTrue(rootCause(notLinked).getMessage().contains("not linked"));
    }

    @Test
    void unlinkExternalRemovesTheIdentityWithoutTouchingData() throws IOException {
        bootstrapEnabled("unlink_external");

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Hubbed").join();
        UUID accountId = Accounts.get().linkExternal(uuid, "site", "gone").join().getAccountId();

        assertTrue(Accounts.get().unlinkExternal("site", "gone").join());
        assertFalse(Accounts.get().findByExternal("site", "gone").join().isPresent());
        assertEquals(accountId, Accounts.get().account(uuid).join().getAccountId(),
                "the platform member stays on its (now single-member) explicit account");
        assertFalse(Accounts.get().unlinkExternal("site", "gone").join(),
                "a second unlink reports the identity as not linked");
    }

    // ------------------------------------------------------------------
    // forced offline reconciliation (the /ecaccount migrate path)
    // ------------------------------------------------------------------

    @Test
    void migrateAccountDataReconcilesOfflinePlayer() throws IOException {
        bootstrapEnabled("migrate_cmd");
        registerSections();

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Waiting").join();
        KillCountSection kills = playerData.getAccountSection(KillCountSection.class).join();
        kills.kills = 6;
        kills.markDirty();
        PlayerController.get().flushAll().join();

        UUID accountId = Accounts.get().linkExternal(uuid, "site", "cmd").join().getAccountId();
        assertEquals(uuid, PlayerController.getLoaded(uuid).getAccountId(),
                "the link does not touch the stamp");

        assertTrue(PlayerController.migrateAccountData(uuid).join(), "the forced run migrates");
        assertEquals(accountId, PlayerController.getLoaded(uuid).getAccountId());
        assertEquals(-1, storedKills(uuid));
        assertEquals(6, storedKills(accountId));

        assertFalse(PlayerController.migrateAccountData(uuid).join(), "nothing left to migrate");
    }

    // ------------------------------------------------------------------
    // admin direct link of two platform identities
    // ------------------------------------------------------------------

    @Test
    void adminLinkFusesTwoPlatformIdentities() throws IOException {
        bootstrapEnabled("admin_link");

        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        PlayerController.handleLogin(uuidA, "Alpha").join();
        PlayerController.handleLogin(uuidB, "Beta").join();

        Account fused = Accounts.get().link(uuidA, uuidB).join();
        assertNotEquals(uuidA, fused.getAccountId(), "two singletons fuse under a MINTED id");
        assertNotEquals(uuidB, fused.getAccountId());
        assertEquals(2, fused.getMembers().size());
        assertEquals(fused.getAccountId(), Accounts.get().account(uuidA).join().getAccountId());
        assertEquals(fused.getAccountId(), Accounts.get().account(uuidB).join().getAccountId());

        //idempotent: re-linking the same pair resolves to the same account
        Account again = Accounts.get().link(uuidA, uuidB).join();
        assertEquals(fused.getAccountId(), again.getAccountId());

        //a third identity joins the EXISTING explicit account (no re-key)
        UUID uuidC = UUID.randomUUID();
        PlayerController.handleLogin(uuidC, "Gamma").join();
        Account extended = Accounts.get().link(uuidA, uuidC).join();
        assertEquals(fused.getAccountId(), extended.getAccountId(),
                "linking into an explicit account keeps its id");
        assertEquals(3, extended.getMembers().size());
    }
}
