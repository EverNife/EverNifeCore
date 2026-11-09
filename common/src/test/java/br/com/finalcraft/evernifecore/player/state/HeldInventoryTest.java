package br.com.finalcraft.evernifecore.player.state;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
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
import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private void holdKit(HoldOptions options) {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, duel, KIT, p -> access.setInventory(p, KIT), options, player);
        access.setInventory(player, KIT);
    }

    @Test
    void theLastHolderToLetGoGivesTheOwnInventoryBack() {
        holdKit(HoldOptions.memory());
        assertEquals(KIT, state.inventory);

        assertTrue(HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.INVENTORY, duel, player));
        assertEquals(OWN, state.inventory);
    }

    @Test
    void anEmptyInventoryIsTheEmptyStringAndComesBackEmpty() {
        state.inventory = "";
        holdKit(HoldOptions.memory());

        HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.INVENTORY, duel, player);
        assertEquals("", state.inventory, "a player who carried nothing ends with nothing, not with the kit");
    }

    @Test
    void aPlayerWhoQuitsMidHoldGetsTheOwnInventoryBackOnTheQuit() {
        holdKit(HoldOptions.memory());

        HeldPlayerState.forget(player.getUniqueId(), player); //what the quit runs, after the region sessions let go
        assertEquals(OWN, state.inventory, "the inventory is written back while the player is still there to write to");
        assertTrue(HeldPlayerState.of(player.getUniqueId()).getHolders(HeldPlayerState.INVENTORY).isEmpty(), "and nothing is held on the rejoin");
    }

    @Test
    void aPersistentHoldComesBackOnTheJoinAfterTheServerStoppedMidHold() {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            holdKit(HoldOptions.persistent());
            assertEquals(OWN, backup().get(HeldPlayerState.INVENTORY), "the own inventory is in the player's data");

            //the server stops mid-hold: no quit runs, the held state in memory is gone with the process
            HeldPlayerState.forget(player.getUniqueId(), null);
            world.close();
            world = PlayerDataWorld.with(Storages.h2("held_inventory")).boot(tempDir);
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            assertEquals(KIT, state.inventory, "the stop left the kit on the player");

            PlayerData data = PlayerController.getLoaded(player.getUniqueId());
            data.setPlayer(player);
            ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
            assertEquals(OWN, state.inventory, "the login gives the own inventory back, no region around");
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
            holdKit(HoldOptions.persistent());
            access.without(IPlayerStateAccess.Capability.INVENTORY); //the write back throws, as a broken item read would

            HeldPlayerState.forget(player.getUniqueId(), player);
            assertEquals(KIT, state.inventory);
            assertEquals(OWN, backup().get(HeldPlayerState.INVENTORY), "a refused write is not a reason to lose the inventory for good");
        } finally {
            world.close();
        }
    }

    @Test
    void anInventoryHeldInMemoryThatCouldNotBeWrittenBackLandsInTheBackupToo() {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory_memory_refused")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            holdKit(HoldOptions.memory());
            assertNull(backup().get(HeldPlayerState.INVENTORY), "memory writes nothing while it can give back");
            access.without(IPlayerStateAccess.Capability.INVENTORY);

            HeldPlayerState.forget(player.getUniqueId(), player);

            assertEquals(OWN, backup().get(HeldPlayerState.INVENTORY), "never lost in silence");
        } finally {
            world.close();
        }
    }

    @Test
    void aLoginAnnouncedAheadOfTheEntityWaitsForItToGiveBack() {
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory_entity_late")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            holdKit(HoldOptions.persistent());
            HeldPlayerState.forget(player.getUniqueId(), null); //the server stops mid-hold

            access.withEntityLate();
            PlayerData data = PlayerController.getLoaded(player.getUniqueId());
            data.setPlayer(player);
            ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
            assertEquals(KIT, state.inventory, "no entity to write to yet");
            assertEquals(OWN, backup().get(HeldPlayerState.INVENTORY));

            access.entityArrived();
            assertEquals(OWN, state.inventory);
            assertNull(backup().get(HeldPlayerState.INVENTORY));
        } finally {
            world.close();
        }
    }

    @Test
    void aWindowInMemoryIsRefusedAndTheMessageNamesTheWayOut() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> HoldOptions.memory().onQuit(QuitPolicy.keepFor(Duration.ofSeconds(10))));

        assertTrue(refused.getMessage().contains("HoldOptions.persistent().onQuit"), refused.getMessage());
    }

    @Test
    void aWindowOfNoTimeIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> QuitPolicy.keepFor(Duration.ZERO));

        assertTrue(refused.getMessage().contains("QuitPolicy.giveBack()"), refused.getMessage());
    }

    @Test
    void theDefaultPolicyGivesBackOnTheQuit() {
        assertSame(QuitPolicy.giveBack(), HoldOptions.memory().getQuitPolicy());
        assertSame(QuitPolicy.giveBack(), HoldOptions.persistent().getQuitPolicy());
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory_default")).boot(tempDir);
        try {
            PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            holdKit(HoldOptions.persistent());

            HeldPlayerState.forget(player.getUniqueId(), player);

            assertEquals(OWN, state.inventory);
            assertNull(backup().get(HeldPlayerState.INVENTORY));
        } finally {
            world.close();
        }
    }

    @Test
    void aLoginWithNothingToGiveBackLeavesNoStateAndAsksThePlatformForNothing() {
        ECoreTestWorld strict = Platforms.strict().onlinePlayers(Collections.emptyList()).mainThreadInline().loggingToStdout().install();
        PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("held_inventory_strict")).boot(tempDir);
        try {
            PlayerData data = PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
            data.setPlayer(player);
            //the strict double refuses getPlayerState, and the world's close fails on a refusal that reached the bus
            ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
        } finally {
            world.close();
            strict.close();
        }
    }

    private PlayerStateBackup backup() {
        return PlayerController.getPDSection(player.getUniqueId(), PlayerStateBackup.class).join();
    }
}
