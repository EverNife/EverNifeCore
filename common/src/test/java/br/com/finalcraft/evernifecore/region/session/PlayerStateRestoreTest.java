package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.PlayerStateSettings;
import br.com.finalcraft.evernifecore.region.playerstate.HeldPlayerState;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerStateBackup;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a player had before a session flag survives a server stop with {@code restore: persistent}, and only then. */
class PlayerStateRestoreTest extends TrackerFixture {

    private PlayerStateBackup backup() {
        return PlayerController.getPDSection(player.getUniqueId(), PlayerStateBackup.class).join();
    }

    @Test
    void persistentGivesTheStateBackOnTheNextJoinAfterTheServerStoppedWithThePlayerInside(TestPlatform platform) {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("region_state_persistent")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            Region zone = region("zone", 0, 10);
            zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
            TestPlayerState.State state = platform.getTestPlayerState().of(player);
            state.allowFlight = true;

            tracker.onJoin(player, at(5.5));
            assertFalse(state.allowFlight);
            assertEquals("true|false", backup().get(HeldPlayerState.FLIGHT), "the flight from before is in the player's data");

            //the server stops with the player inside: no quit runs, the session and its memory are gone
            tracker.stop();
            world.close(); //a restart: a fresh PlayerData layer over the same storage
            world = PlayerDataWorld.with(Storages.h2("region_state_persistent")).boot(tempDir);
            tracker = new RegionMovementTracker(lookup, bus);
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            assertEquals("true|false", backup().get(HeldPlayerState.FLIGHT), "read back from storage");

            tracker.onJoin(player, at(30.5));
            assertTrue(state.allowFlight, "back outside, the player gets the flight they had");
            assertNull(backup().get(HeldPlayerState.FLIGHT), "and the backup lets it go");
        } finally {
            world.close();
        }
    }

    @Test
    void persistentRejoiningInsideKeepsTheStateFromBeforeTheStop(TestPlatform platform) {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("region_state_inside")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            Region zone = region("zone", 0, 10);
            zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
            TestPlayerState.State state = platform.getTestPlayerState().of(player);
            state.allowFlight = true;
            tracker.onJoin(player, at(5.5));

            tracker.stop();
            world.close(); //a restart: a fresh PlayerData layer over the same storage
            world = PlayerDataWorld.with(Storages.h2("region_state_inside")).boot(tempDir);
            tracker = new RegionMovementTracker(lookup, bus);
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            tracker.onJoin(player, at(5.5)); //inside again, still grounded
            tracker.onMove(player, at(5.5), at(30.5), MoveCause.MOVE, null);
            assertTrue(state.allowFlight, "the flight from before the stop, not the grounded state found on the join");
        } finally {
            world.close();
        }
    }

    @Test
    void memoryWritesNothingAndGivesTheStateBackOnQuit(TestPlatform platform) {
        try (PlayerDataWorld ignored = PlayerDataWorld.with(Storages.h2("region_state_memory")).boot(tempDir)) {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            Region zone = region("zone", 0, 10);
            zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
            zone.<PlayerStateSettings>editFlagSettings(CoreFlags.ALLOW_FLY, settings -> settings.setRestore(PlayerStateSettings.Restore.MEMORY));
            TestPlayerState.State state = platform.getTestPlayerState().of(player);
            state.allowFlight = true;

            tracker.onJoin(player, at(5.5));
            assertFalse(state.allowFlight);
            assertTrue(backup().getSlots().isEmpty(), "memory writes nothing to the player's data");
            tracker.onQuit(player);
            assertTrue(state.allowFlight, "the quit gives the flight back");
        }
    }
}
