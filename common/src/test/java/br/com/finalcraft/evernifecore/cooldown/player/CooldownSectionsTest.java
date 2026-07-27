package br.com.finalcraft.evernifecore.cooldown.player;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.CooldownEntry;
import br.com.finalcraft.evernifecore.cooldown.CooldownRetention;
import br.com.finalcraft.evernifecore.cooldown.GenericCooldown;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two player-cooldown quadrants ({@link PlayerCooldownsLocal}, {@link PlayerCooldownsNetwork}),
 * the stop-tombstone that has to reach a replicated store, the retention horizon and its
 * tombstone carve-out, and the account absorption a link triggers. Backend: H2 in-memory (the same
 * durable DB across two bootstraps stands in for two servers).
 */
@ECoreTest
class CooldownSectionsTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    // ================================================================================================
    // Tombstone routing (the crux the value-level latest test cannot catch): a stop must reach the
    // STORED map on the network row, and must be forgotten from it on the local row.
    // ================================================================================================

    @Test
    void aNetworkStopIsStoredWhileALocalStopIsForgotten() {
        UUID uuid = UUID.randomUUID();

        //network: absence is ambiguous, so a stop is kept as a zeroed anchor in the STORED map
        PlayerCooldownsNetwork network = new PlayerCooldownsNetwork();
        Cooldown networkHandle = network.cooldown(uuid, "vip").setPersist(true).startWith(60);
        assertTrue(network.getPersistedCooldowns().containsKey("vip"));
        networkHandle.stop();
        CooldownEntry tombstone = network.getPersistedCooldowns().get("vip");
        assertNotNull(tombstone, "a network stop must be stored, not forgotten - a peer would resurrect it");
        assertEquals(0L, tombstone.getTimeStart(), "the stored entry is the zeroed anchor");
        assertNull(network.getTransientCooldowns().get("vip"), "and never slips into the memory-only map");

        //local: absence means free here, so a stop is simply dropped from the stored map
        PlayerCooldownsLocal local = new PlayerCooldownsLocal();
        Cooldown localHandle = local.cooldown("vip").setPersist(true).startWith(60);
        assertTrue(local.getPersistedCooldowns().containsKey("vip"));
        localHandle.stop();
        assertNull(local.getPersistedCooldowns().get("vip"), "a local stop is forgotten from storage");
    }

    // ================================================================================================
    // (b2) A stop survives the real route -> flush -> merge -> prune path.
    // ================================================================================================

    @Test
    void aNetworkStopSurvivesRoundTripMergeAndPrune() throws IOException, InterruptedException {
        File storageYml = writeStorageYml("net_stop_roundtrip", true);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Vip").join();
        PlayerCooldownsNetwork started = playerData.getAccountSection(PlayerCooldownsNetwork.class).join();
        started.cooldown(uuid, "vip").setPersist(true).startWith(60);
        PlayerController.get().flushAll().join();

        //a second server reads the started row back from the backend and stops the cooldown there
        PlayerController.initialize(storageYml);
        PlayerCooldownsNetwork peer = PlayerController
                .getAccountSectionByAccountId(uuid, PlayerCooldownsNetwork.class).join();
        //the stop is a later event than the start; make its mutation clock observably newer even on a
        //coarse (~15ms) system clock, so the merge decides by the clock and not by the expiry fallback
        Thread.sleep(40);
        peer.cooldown(uuid, "vip").stop();
        assertEquals(0L, peer.getPersistedCooldowns().get("vip").getTimeStart(),
                "the stop must land in the STORED map - otherwise it never reaches the backend");
        assertNull(peer.getTransientCooldowns().get("vip"));
        PlayerController.get().flushAll().join();

        //the first server's still-started replica merges the peer's stopped state
        PlayerCooldownsNetwork merged = started.merge(Collections.singletonList(peer));
        assertFalse(merged.cooldown(uuid, "vip").isInCooldown(), "the newer stop must win the merge");

        //and a prune inside the retention horizon keeps the tombstone: it is not resurrected
        merged.pruneExpired(System.currentTimeMillis());
        assertNotNull(merged.getPersistedCooldowns().get("vip"), "a fresh tombstone survives the prune");
        assertFalse(merged.cooldown(uuid, "vip").isInCooldown(), "and stays stopped after the prune");
    }

    // ================================================================================================
    // (c) GC keeps what a bigger read still sees, and drops what is past the horizon; a tombstone is
    // judged by its mutation clock, never by the always-true normal formula.
    // ================================================================================================

    @Test
    void gcKeepsWhatALongerReadStillSeesAndDropsWhatIsPastTheHorizon() {
        long now = System.currentTimeMillis();
        long retention = TimeUnit.DAYS.toMillis(30);
        long duration = TimeUnit.SECONDS.toMillis(300);

        //started 400s ago, nominally 300s: already past its own end, still inside the retention window
        CooldownEntry within = new CooldownEntry(now - TimeUnit.SECONDS.toMillis(400), duration,
                now - TimeUnit.SECONDS.toMillis(400), true);
        assertFalse(CooldownRetention.isExpired(within, now, retention), "still inside the retention horizon");
        assertTrue(new GenericCooldown("vip", within).isInCooldown(600),
                "a 600s reading still sees it, so GC must not drop it");

        //started far enough back to be past its end PLUS the retention window
        CooldownEntry past = new CooldownEntry(now - duration - retention - 1000, duration,
                now - duration - retention - 1000, true);
        assertTrue(CooldownRetention.isExpired(past, now, retention), "past the horizon");

        Map<String, CooldownEntry> map = new LinkedHashMap<>();
        map.put("live", within);
        map.put("dead", past);
        assertTrue(CooldownRetention.prune(map, now));
        assertTrue(map.containsKey("live"), "the prune keeps what a longer read can still see");
        assertFalse(map.containsKey("dead"));
    }

    @Test
    void aTombstoneIsPrunedByItsMutationClockNotTheAlwaysTrueNormalFormula() {
        long now = System.currentTimeMillis();
        long retention = TimeUnit.DAYS.toMillis(30);

        //timeStart == 0: the normal 'timeStart + duration + retention' horizon is in the past, which
        //would drop a fresh tombstone on its first prune and reopen the resurrection it exists to close
        CooldownEntry fresh = new CooldownEntry(0L, TimeUnit.SECONDS.toMillis(60), now, false);
        assertFalse(CooldownRetention.isExpired(fresh, now, retention),
                "a fresh tombstone must survive - it is what out-votes a lagging peer's old start");

        CooldownEntry old = new CooldownEntry(0L, TimeUnit.SECONDS.toMillis(60), now - retention - 1000, false);
        assertTrue(CooldownRetention.isExpired(old, now, retention),
                "past the mutation-clock horizon the tombstone is finally droppable");
    }

    // ================================================================================================
    // (d) Two servers over the same backend see each other's network cooldown.
    // ================================================================================================

    @Test
    void twoServersSeeEachOthersNetworkCooldown() throws IOException {
        File storageYml = writeStorageYml("net_two_servers", true);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Vip").join();
        playerData.getAccountSection(PlayerCooldownsNetwork.class).join()
                .cooldown(uuid, "vip").setPersist(true).startWith(300);
        PlayerController.get().flushAll().join();

        //a fresh controller over the same durable backend stands in for the other server
        PlayerController.initialize(storageYml);
        PlayerCooldownsNetwork serverB = PlayerController
                .getAccountSectionByAccountId(uuid, PlayerCooldownsNetwork.class).join();
        assertTrue(serverB.cooldown(uuid, "vip").isInCooldown(),
                "the other server must see the network cooldown this one started");
    }

    // ================================================================================================
    // (g) A link absorbs a network cooldown into the account row (the merge does not break absorption).
    // ================================================================================================

    @Test
    void linkAbsorbsANetworkCooldownIntoTheAccountRow() throws IOException {
        PlayerController.initialize(writeStorageYml("net_link_absorb", true));

        UUID memberUuid = UUID.randomUUID();
        PlayerData member = PlayerController.handleLogin(memberUuid, "Alt").join();
        member.getAccountSection(PlayerCooldownsNetwork.class).join()
                .cooldown(memberUuid, "vip").setPersist(true).startWith(600);
        PlayerController.get().flushAll().join();

        //the identity is now linked into a canonical account: its old key becomes a former key
        UUID canonicalId = persistLinkedAccount(memberUuid, "Alt");

        //the next login absorbs the former-key row into the canonical account row
        PlayerData relogged = PlayerController.handleLogin(memberUuid, "Alt").join();
        PlayerCooldownsNetwork canonical = relogged.getAccountSection(PlayerCooldownsNetwork.class).join();
        assertEquals(canonicalId, canonical.getAccountId(), "the row now keys by the canonical account id");
        assertTrue(canonical.cooldown(memberUuid, "vip").isInCooldown(),
                "the network cooldown must follow the identity into the account");
    }

    // ================================================================================================
    // (e) A cold bucket must not answer "free": a stored cooldown is read from the backend on a miss.
    // ================================================================================================

    @Test
    void aColdBucketResolvesTheStoredCooldownInsteadOfAnsweringFree() throws IOException {
        File storageYml = writeStorageYml("local_cold_bucket", true);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerData player = PlayerController.handleLogin(uuid, "Vip").join();
        player.getPDSection(PlayerCooldownsLocal.class).join()
                .cooldown("kit").setPersist(true).startWith(300);
        PlayerController.get().flushAll().join();

        //a fresh server with a cold cache over the same backend: the bucket is not loaded
        PlayerController.initialize(storageYml);
        PlayerCooldown fromBackend = PlayerCooldown.of(uuid, "kit").join();
        assertTrue(fromBackend.isInCooldown(),
                "a cold bucket must read the stored cooldown, never report an empty one as free");
    }

    // ================================================================================================
    // (f) The broken pieces work again: getCooldown does not throw, a persistent player cooldown
    // survives a reboot, and the resetplayer path (resolve -> stop) does not blow up.
    // ================================================================================================

    @Test
    void getCooldownNoLongerThrowsAndAPersistentPlayerCooldownSurvivesAReboot() throws IOException {
        File storageYml = writeStorageYml("local_reboot", true);
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerData player = PlayerController.handleLogin(uuid, "Vip").join();
        //getCooldown used to throw UnsupportedOperationException - now it resolves a real handle
        PlayerCooldown cooldown = player.getCooldown("daily").join();
        assertNotNull(cooldown, "getCooldown must resolve a handle, not throw");
        cooldown.setPersist(true).startWith(600);
        PlayerController.get().flushAll().join();

        //reboot: a persistent player cooldown must still be in effect
        PlayerController.initialize(storageYml);
        PlayerData relogged = PlayerController.handleLogin(uuid, "Vip").join();
        PlayerCooldown afterReboot = relogged.getCooldown("daily").join();
        assertTrue(afterReboot.isInCooldown(), "a persistent player cooldown must survive a reboot");

        //the /cooldown resetplayer core (resolve async, then stop) must not blow up and must clear it
        afterReboot.stop();
        assertFalse(afterReboot.isInCooldown(), "resetplayer's stop must clear the cooldown");
    }

    // ================================================================================================
    // merge purity (the AccountSection contract the network row must not break)
    // ================================================================================================

    @Test
    void mergeIsPureAndCombinesReplicas() {
        long now = System.currentTimeMillis();
        PlayerCooldownsNetwork a = new PlayerCooldownsNetwork();
        a.getPersistedCooldowns().put("kit", new CooldownEntry(now, 60_000L, now, true));
        PlayerCooldownsNetwork b = new PlayerCooldownsNetwork();
        b.getPersistedCooldowns().put("home", new CooldownEntry(now, 60_000L, now, true));

        PlayerCooldownsNetwork merged = a.merge(Collections.singletonList(b));

        assertNotSame(a, merged);
        assertNotSame(b, merged);
        assertTrue(merged.getPersistedCooldowns().containsKey("kit"));
        assertTrue(merged.getPersistedCooldowns().containsKey("home"));
        assertEquals(1, a.getPersistedCooldowns().size(), "the receiver must not be mutated");
        assertEquals(1, b.getPersistedCooldowns().size(), "an input must not be mutated");
    }

    // ================================================================================================
    // fixtures
    // ================================================================================================

    private File writeStorageYml(String dbName, boolean accountsEnabled) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "multi-platform-accounts:",
                "  enabled: " + accountsEnabled,
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
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
}
