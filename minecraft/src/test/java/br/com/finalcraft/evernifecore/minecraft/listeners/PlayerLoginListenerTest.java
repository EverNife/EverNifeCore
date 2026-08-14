package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.minecraft.testkit.Doubles;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What this platform announces on the event bus: a login only once the player is in-game with their
 * data attached, and a quit while that data is still attached.
 *
 * <p>The AuthMe half of the login is driven through the producer itself rather than through
 * {@code LoginEvent}: AuthMe is a compile-only jar and is not on the test classpath.</p>
 */
class PlayerLoginListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitEventWorld world;

    @BeforeEach
    void bootTheServerAndThePlayerData() {
        world = BukkitEventWorld.install(tempDir);
        PlayerController.initialize(Storages.memory().writeTo(tempDir));
    }

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        world.close();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  ECPlayerFullyLoggedInEvent
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theLoginIsAnnouncedATickAfterTheJoin() {
        PlayerData playerData = loggedInPlayer("Petrus");
        Player player = onlinePlayer(playerData.getUniqueId());

        List<ECPlayerFullyLoggedInEvent> heard = new ArrayList<>();
        world.subscribe(ECPlayerFullyLoggedInEvent.class, heard::add);

        new PlayerLoginListener.VanillaLogin().onPlayerLogin(new PlayerJoinEvent(player, "joined"));
        assertTrue(heard.isEmpty(), "the join alone announces nothing: the data is attached a tick later");

        world.runScheduledTasks();

        assertEquals(1, heard.size());
        assertEquals(playerData, heard.get(0).getPlayerData());
        assertFalse(heard.get(0).isExternalAuthLogin(), "a vanilla join needed no external auth flow");
    }

    @Test
    void theLoginAlsoReachesAPlainBukkitListener() {
        PlayerData playerData = loggedInPlayer("Mirrored");
        Player player = onlinePlayer(playerData.getUniqueId());

        List<ECPlayerFullyLoggedInEvent> heard = new ArrayList<>();
        world.listen(ECPlayerFullyLoggedInEvent.class, heard::add);

        new PlayerLoginListener.VanillaLogin().onPlayerLogin(new PlayerJoinEvent(player, "joined"));
        world.runScheduledTasks();

        assertEquals(1, heard.size(), "the event is a Bukkit event too, and the audience mirrors it");
        assertEquals(playerData, heard.get(0).getPlayerData());
    }

    @Test
    void aLoginBehindAnExternalAuthFlowSaysSo() {
        PlayerData playerData = loggedInPlayer("Authenticated");
        Player player = onlinePlayer(playerData.getUniqueId());

        List<ECPlayerFullyLoggedInEvent> heard = new ArrayList<>();
        world.subscribe(ECPlayerFullyLoggedInEvent.class, heard::add);

        PlayerLoginListener.fireDelayedFullyLoggedInEvent(player, true);
        world.runScheduledTasks();

        assertEquals(1, heard.size());
        assertTrue(heard.get(0).isExternalAuthLogin());
    }

    @Test
    void aPlayerWhoLeftBeforeTheTickIsNeverAnnounced() {
        PlayerData playerData = loggedInPlayer("Gone");
        AtomicBoolean online = new AtomicBoolean(true);
        Player player = Doubles.of(Player.class)
                .on("getUniqueId", args -> playerData.getUniqueId())
                .on("getName", args -> playerData.getName())
                .on("isOnline", args -> online.get())
                .build();

        List<ECPlayerFullyLoggedInEvent> heard = new ArrayList<>();
        world.subscribe(ECPlayerFullyLoggedInEvent.class, heard::add);

        new PlayerLoginListener.VanillaLogin().onPlayerLogin(new PlayerJoinEvent(player, "joined"));
        online.set(false);
        world.runScheduledTasks();

        assertTrue(heard.isEmpty(), "the tick found nobody to announce");
    }

    @Test
    void aPlayerWithoutDataIsNeverAnnounced() {
        //no handleLogin for this one: the whitelist and the fake-player paths both get here
        Player player = onlinePlayer(UUID.randomUUID());

        List<ECPlayerFullyLoggedInEvent> heard = new ArrayList<>();
        world.subscribe(ECPlayerFullyLoggedInEvent.class, heard::add);

        new PlayerLoginListener.VanillaLogin().onPlayerLogin(new PlayerJoinEvent(player, "joined"));
        world.runScheduledTasks();

        assertTrue(heard.isEmpty(), "there is no PlayerData to hand a handler");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  ECPlayerQuitEvent
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theQuitIsAnnouncedWhileTheDataIsStillAttached() {
        PlayerData playerData = loggedInPlayer("Leaving");
        Player player = onlinePlayer(playerData.getUniqueId());

        List<FPlayer> attachedWhenHeard = new ArrayList<>();
        world.subscribe(ECPlayerQuitEvent.class, event -> attachedWhenHeard.add(event.getPlayer()));

        new PlayerLoginListener().onPlayerQuitEvent(new PlayerQuitEvent(player, "left"));

        assertEquals(1, attachedWhenHeard.size());
        assertNotNull(attachedWhenHeard.get(0), "a handler runs BEFORE the detach, so it still sees the player");
        assertNull(playerData.getPlayer(), "and the detach did run, right after");
    }

    @Test
    void aQuitWithoutDataAnnouncesNothing() {
        Player player = onlinePlayer(UUID.randomUUID());

        List<ECPlayerQuitEvent> heard = new ArrayList<>();
        world.subscribe(ECPlayerQuitEvent.class, heard::add);

        new PlayerLoginListener().onPlayerQuitEvent(new PlayerQuitEvent(player, "left"));

        assertTrue(heard.isEmpty(), "there is no PlayerData to hand a handler");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** A player the controller loaded and whose live player is attached - what a finished login leaves. */
    private static PlayerData loggedInPlayer(String name) {
        PlayerData playerData = PlayerController.handleLogin(UUID.randomUUID(), name).join();
        playerData.setPlayer(Doubles.of(FPlayer.class)
                .on("getUniqueId", args -> playerData.getUniqueId())
                .on("getName", args -> name)
                .on("isOnline", args -> Boolean.TRUE)
                .build());
        return playerData;
    }

    private static Player onlinePlayer(UUID uuid) {
        return Doubles.of(Player.class)
                .on("getUniqueId", args -> uuid)
                .on("isOnline", args -> Boolean.TRUE)
                .build();
    }

}
