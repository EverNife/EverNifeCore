package br.com.finalcraft.evernifecore.cooldown.server;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.CooldownEntry;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server-cooldown quadrants: the network reach of {@link Cooldown#network(String)} seen across
 * two servers, the born-persistent handle, the stop a later resolve must not revive, and the
 * {@link ServerCooldownRow} convergence by {@link CooldownEntry#latest}. Backend: H2 in-memory (the
 * same durable DB across two bootstraps stands in for two servers).
 */
class ServerCooldownsTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
        PlayerController.getConfiguredAccountSections().clear();
        EntitySchemaMigrations.clear();
    }

    // ================================================================================================
    // Two servers over the same backend see each other's SERVER network cooldown, and the
    // ServerCooldownRow converges by latest() on a conflict.
    // ================================================================================================

    @Test
    void twoServersSeeEachOthersServerNetworkCooldown() throws IOException {
        File storageYml = writeStorageYml("srv_net_two_servers");
        PlayerController.bootstrap(storageYml);

        Cooldown.network("global_event").setPersist(true).startWith(300);
        PlayerController.get().flushAll().join(); //awaits the network cooldown's in-flight writes

        //a fresh controller over the same durable backend stands in for the other server
        PlayerController.bootstrap(storageYml);
        assertTrue(Cooldown.network("global_event").isInCooldown(),
                "the other server must see the server-wide network cooldown this one started");
    }

    @Test
    void networkCooldownReplicatesWithoutExplicitSetPersist() throws IOException {
        File storageYml = writeStorageYml("srv_net_born_persistent");
        PlayerController.bootstrap(storageYml);

        //no explicit setPersist(true): the server-network handle must be born persistent on its own
        Cooldown handle = Cooldown.network("global_event");
        assertTrue(handle.isPersistent(),
                "a server-network cooldown handle must be born persistent, no external setPersist needed");
        handle.startWith(300);
        PlayerController.get().flushAll().join(); //awaits the network cooldown's in-flight writes

        //a fresh controller over the same durable backend stands in for the other server
        PlayerController.bootstrap(storageYml);
        assertTrue(Cooldown.network("global_event").isInCooldown(),
                "the other server must see a network cooldown started without a manual setPersist");
    }

    @Test
    void aStoppedServerCooldownStaysFreeEvenAfterAResolveReasserts() throws IOException {
        File storageYml = writeStorageYml("srv_net_stop_then_resolve");
        PlayerController.bootstrap(storageYml);

        Cooldown.network("global_event").startWith(300);
        Cooldown.network("global_event").stop();
        //resolve() re-asserts persist=true on the shared entry; that must not revive a stopped cooldown,
        //since isInCooldown is gated on timeStart (a stop zeroes it), not on the persist flag
        assertFalse(Cooldown.network("global_event").isInCooldown(),
                "a stopped server cooldown must stay free even after resolve re-marks the entry persistent");
    }

    @Test
    void aServerCooldownRowConvergesByLatestOnAConflict() {
        ServerCooldownRow local = new ServerCooldownRow("vip");
        local.getEntry().adoptState(new CooldownEntry(100L, 60_000L, 100L, true));

        ServerCooldownRow stored = new ServerCooldownRow("vip");
        stored.getEntry().adoptState(new CooldownEntry(0L, 60_000L, 200L, true)); //a newer stop

        CooldownEntry sharedInstance = local.getEntry();
        local.mergeStoredState(stored);

        assertSame(sharedInstance, local.getEntry(),
                "the shared entry instance must be mutated in place, never swapped out");
        assertEquals(0L, local.getEntry().getTimeStart(), "the newer stop wins the convergence by latest()");
        assertTrue(local.isDirty(), "a converged row is re-marked dirty to persist what survived");
    }

    // ================================================================================================
    // fixtures
    // ================================================================================================

    private File writeStorageYml(String dbName) throws IOException {
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
        return file;
    }
}
