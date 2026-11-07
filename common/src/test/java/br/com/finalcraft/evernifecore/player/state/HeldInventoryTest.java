package br.com.finalcraft.evernifecore.player.state;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player's whole inventory held aside - a duel kit, an arena - and given back, even across a quit or a server stop. */
class HeldInventoryTest {

    private static final String OWN = "own: sword, 12 bread";
    private static final String KIT = "kit: bow, 64 arrows";

    @TempDir
    Path tempDir;

    private ECoreTestWorld platform;
    private TestPlayerState access;
    private final StateHolder duel = StateHolder.of("theduel:battle");
    private TestFPlayerSender player;
    private TestPlayerState.State state;

    @BeforeEach
    void setUp() {
        platform = Platforms.lenient().playerState().install();
        Plugins.fakeCore(tempDir.resolve("core").toFile());
        player = new TestFPlayerSender("duelist");
        access = platform.platform().getTestPlayerState();
        state = access.of(player);
        state.inventory = OWN;
    }

    @AfterEach
    void tearDown() {
        HeldPlayerState.forget(player.getUniqueId(), null);
        Plugins.forget(Plugins.CORE);
        platform.close();
    }

    private void holdKit(boolean persistent) {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, duel, KIT, p -> access.setInventory(p, KIT), persistent, player);
        access.setInventory(player, KIT);
    }

    @Test
    void theLastHolderToLetGoGivesTheOwnInventoryBack() {
        holdKit(false);
        assertEquals(KIT, state.inventory);

        assertTrue(HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.INVENTORY, duel, player));
        assertEquals(OWN, state.inventory);
    }

    @Test
    void anEmptyInventoryIsTheEmptyStringAndComesBackEmpty() {
        state.inventory = "";
        holdKit(false);

        HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.INVENTORY, duel, player);
        assertEquals("", state.inventory, "a player who carried nothing ends with nothing, not with the kit");
    }

    @Test
    void aPlayerWhoQuitsMidHoldGetsTheOwnInventoryBackOnTheQuit() {
        holdKit(false);

        HeldPlayerState.forget(player.getUniqueId(), player); //what the quit runs, after the region sessions let go
        assertEquals(OWN, state.inventory, "the inventory is written back while the player is still there to write to");
        assertTrue(HeldPlayerState.of(player.getUniqueId()).getHolders(HeldPlayerState.INVENTORY).isEmpty(), "and nothing is held on the rejoin");
    }

    @Test
    void aPersistentHoldComesBackOnTheJoinAfterTheServerStoppedMidHold() {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            holdKit(true);
            assertEquals(OWN, backup().get(HeldPlayerState.INVENTORY), "the own inventory is in the player's data");

            //the server stops mid-hold: no quit runs, the held state in memory is gone with the process
            HeldPlayerState.forget(player.getUniqueId(), null);
            world.close();
            world = PlayerDataWorld.with(Storages.h2("held_inventory")).boot(tempDir);
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            assertEquals(KIT, state.inventory, "the stop left the kit on the player");

            HeldPlayerState.of(player.getUniqueId()).restoreLeftovers(player);
            assertEquals(OWN, state.inventory, "the join gives the own inventory back");
            assertNull(backup().get(HeldPlayerState.INVENTORY), "and the backup lets it go");
        } finally {
            world.close();
        }
    }

    @Test
    void anInventoryThatCouldNotBeWrittenBackStaysInTheBackupForTheNextJoin() {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory_refused")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            holdKit(true);
            access.without(IPlayerStateAccess.Capability.INVENTORY); //the write back throws, as a broken item read would

            HeldPlayerState.forget(player.getUniqueId(), player);
            assertEquals(KIT, state.inventory);
            assertEquals(OWN, backup().get(HeldPlayerState.INVENTORY), "a refused write is not a reason to lose the inventory for good");
        } finally {
            world.close();
        }
    }

    private PlayerStateBackup backup() {
        return PlayerController.getPDSection(player.getUniqueId(), PlayerStateBackup.class).join();
    }
}
