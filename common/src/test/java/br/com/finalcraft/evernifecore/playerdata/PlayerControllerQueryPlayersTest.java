package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.storage.config.PlayerDataAdminConfig;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole PlayerData base is reachable through the public facade, not only the part a RECENT load
 * mode keeps in memory - and a loaded player comes back as the instance everyone else holds.
 */
@ECoreTest
class PlayerControllerQueryPlayersTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    @Test
    void beforeTheBootstrapTheLoadModeIsRefused() {
        assertThrows(IllegalStateException.class, PlayerController::getLoadMode);
    }

    @Test
    void aStoredPlayerOutsideTheRecentWindowIsFoundAndALoadedOneIsTheLiveInstance() throws Exception {
        String db = "d_query_players";
        PlayerController.initialize(Storages.h2(db).loadModeAll().fileName("storage_all.yml").writeTo(tempDir));
        assertEquals(PlayerDataAdminConfig.LoadMode.ALL, PlayerController.getLoadMode());

        UUID sleeper = UUID.randomUUID();
        PlayerData sleeperData = PlayerController.handleLogin(sleeper, "Sleeper").join();
        sleeperData.lastSeen = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(120);
        sleeperData.markDirty();
        UUID active = UUID.randomUUID();
        PlayerController.handleLogin(active, "Active").join();
        PlayerController.get().flushAll().join();
        PlayerController.shutdown();

        PlayerController.initialize(Storages.h2(db).loadModeRecent(1).fileName("storage_recent.yml").writeTo(tempDir));
        assertEquals(PlayerDataAdminConfig.LoadMode.RECENT, PlayerController.getLoadMode());
        assertFalse(PlayerController.getAllLoaded().stream().anyMatch(p -> p.getUniqueId().equals(sleeper)),
                "the precondition: RECENT left the old player out of memory");

        List<PlayerData> everyone = PlayerController.queryPlayers(Query.all()).join();
        assertTrue(everyone.stream().anyMatch(p -> p.getUniqueId().equals(sleeper)),
                "the query reaches the stored player that is not in memory");
        PlayerData activeMatch = everyone.stream().filter(p -> p.getUniqueId().equals(active)).findFirst().get();
        assertSame(PlayerController.getLoaded(active), activeMatch,
                "a loaded player comes back as the live instance, not a detached copy");

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        List<PlayerData> old = PlayerController.queryPlayers(Query.range("lastSeen", null, cutoff)).join();
        assertEquals(1, old.size());
        assertEquals(sleeper, old.get(0).getUniqueId());
    }
}
