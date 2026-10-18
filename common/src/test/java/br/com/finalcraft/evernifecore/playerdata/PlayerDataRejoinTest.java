package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A login hands out every section that loads on login, also for a player whose PlayerData is already in memory: one
 * who left and came back in the same server run after the idle grace released his sections, or one looked up before
 * he joined. A lookup alone never loads them.
 */
@ECoreTest
class PlayerDataRejoinTest {

    @TempDir
    Path tempDir;

    public static class OnlineSection extends PDSection {
        public long value;
    }

    public static class LazySection extends PDSection {
        public long value;
    }

    //grace zero: the quit releases the cells on the spot, so the test needs no clock
    private PlayerDataWorld world(String dbName) {
        return PlayerDataWorld.with(Storages.h2(dbName))
                .section(PDSectionConfiguration.builder(null, OnlineSection.class, "online")
                        .lifecycle(SectionLifecycle.ONLINE).idleGrace(Duration.ZERO).build())
                .section(PDSectionConfiguration.builder(null, LazySection.class, "lazy")
                        .lifecycle(SectionLifecycle.LAZY).idleGrace(Duration.ZERO).build())
                .boot(tempDir);
    }

    /** Writes a value to both sections, flushes, quits, and checks the grace released both cells. */
    private static void playOneSessionAndLeave(UUID uuid, String name) {
        PlayerController.handleLogin(uuid, name).join();
        PlayerData playerData = PlayerController.getLoaded(uuid);
        OnlineSection online = playerData.getPDSection(OnlineSection.class).join();
        online.value = 42L;
        online.markDirty();
        LazySection lazy = playerData.getPDSection(LazySection.class).join();
        lazy.value = 7L;
        lazy.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.handlePlayerQuit(uuid);
        assertNull(PlayerController.getLoadedSection(uuid, OnlineSection.class), "the grace released the ONLINE cell");
        assertNull(PlayerController.getLoadedSection(uuid, LazySection.class), "the grace released the LAZY cell");
    }

    @Test
    void rejoinAfterTheGraceReleasedTheSectionsLoadsTheOnlineOneAgain() {
        try (PlayerDataWorld ignored = world("rejoin_online")) {
            UUID uuid = UUID.randomUUID();
            playOneSessionAndLeave(uuid, "Rejoin");

            PlayerController.handleLogin(uuid, "Rejoin").join();

            OnlineSection online = PlayerController.getLoadedSection(uuid, OnlineSection.class);
            assertNotNull(online, "the login loads the ONLINE section again, even with the PlayerData still in memory");
            assertEquals(42L, online.value, "read back from storage");
        }
    }

    @Test
    void rejoinLeavesTheLazySectionOutAndAnOfflineLookupLoadsNothing() {
        try (PlayerDataWorld ignored = world("rejoin_contract")) {
            UUID returning = UUID.randomUUID();
            UUID offline = UUID.randomUUID();
            playOneSessionAndLeave(returning, "Returning");
            playOneSessionAndLeave(offline, "Offline");

            PlayerController.handleLogin(returning, "Returning").join();
            assertNull(PlayerController.getLoadedSection(returning, LazySection.class),
                    "a LAZY section waits for someone to ask, on a rejoin too");

            assertNotNull(PlayerController.getPlayerData(offline).join());
            assertNull(PlayerController.getLoadedSection(offline, OnlineSection.class),
                    "looking up a player who is not logging in loads none of his sections");
        }
    }

    @Test
    void loginAfterARebootLoadsTheOnlineSectionAndALookupBeforeItDoesNot() {
        try (PlayerDataWorld world = world("rejoin_reboot")) {
            UUID uuid = UUID.randomUUID();
            playOneSessionAndLeave(uuid, "Reboot");

            world.reboot();

            assertNotNull(PlayerController.getPlayerData(uuid).join(), "the player is still in storage");
            assertNull(PlayerController.getLoadedSection(uuid, OnlineSection.class),
                    "a lookup before the login loads none of his sections");

            PlayerController.handleLogin(uuid, "Reboot").join();
            OnlineSection online = PlayerController.getLoadedSection(uuid, OnlineSection.class);
            assertNotNull(online, "the first login after a restart loads the ONLINE section");
            assertEquals(42L, online.value, "read back from storage");
            assertNull(PlayerController.getLoadedSection(uuid, LazySection.class), "the LAZY one still waits");
        }
    }
}
