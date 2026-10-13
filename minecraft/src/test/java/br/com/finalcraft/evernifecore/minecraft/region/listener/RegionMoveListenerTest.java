package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.event.RegionBoundaryEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyTeleportInFlag;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld.isRegistered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Players crossing region borders on a Bukkit server: the seven ways a player changes place, and the listener's own coming and going. */
class RegionMoveListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** The arena, 0..10 on every axis, that nobody may walk into. */
    private static Region arena(RegionServer server) {
        return server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(new DenyEntryFlag()));
    }

    private static PlayerMoveEvent step(RegionServer server, Player player, double fromX, double toX) {
        return new PlayerMoveEvent(player, server.at(fromX, 5, 5.5), server.at(toX, 5, 5.5));
    }

    /** Whether the step did not land where it was headed, by whatever means the listener stopped it. */
    private static boolean denied(PlayerMoveEvent step, double headedToX) {
        return step.isCancelled() || step.getTo().getX() != headedToX;
    }

    /** A minecart carrying {@code riders} from x = {@code fromX} to {@code toX}, recording what was done to it. */
    private static final class Cart {
        final AtomicInteger ejected = new AtomicInteger();
        final AtomicReference<Location> sentTo = new AtomicReference<>();
        final Minecart vehicle;

        Cart(RegionServer server, Player... riders) {
            vehicle = RegionServer.entity(Minecart.class, EntityType.MINECART, server.at(12.5, 5, 5.5))
                    .returning("getPassengers", new ArrayList<Entity>(Arrays.asList(riders)))
                    .on("eject", args -> ejected.incrementAndGet() > 0)
                    .on("teleport", args -> {
                        sentTo.set((Location) args[0]);
                        return true;
                    })
                    .build();
        }
    }

    @Test
    void aStepIntoDenyEntryIsRevertedBySetTo() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            arena(server);
            server.listen(new RegionMoveListener());
            Player player = server.player("walker", 12.7, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));

            PlayerMoveEvent first = step(server, player, 12.7, 10.5);
            first.getTo().setYaw(90);
            server.server.getPluginManager().callEvent(first);

            assertFalse(first.isCancelled(), "a denied step is put back, never cancelled");
            assertEquals(new Location(server.world, 12.5, 5, 5.5, 90, 0), first.getTo(), "the center of the last valid block, facing where they turned");
            PlayerMoveEvent second = server.call(step(server, player, 12.7, 10.5));
            assertEquals(12.5, second.getTo().getX(), "nothing changed in the session: the same step is denied again");
        }
    }

    @Test
    void aHeadTurnNeverReachesTheTracker() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("plaza", 0, 0, 0, 10, 10, 10);
            server.listen(new RegionMoveListener());
            List<RegionEnterEvent> enters = server.regions.recorded(RegionEnterEvent.class);
            Player player = server.player("looker", 5.5, 5, 5.5);
            assertFalse(RegionLookup.get().at(WorldLocPos.of(5.5, 5, 5.5, RegionServer.WORLD)).isEmpty(), "the player stands in the plaza");

            Location from = server.at(5.5, 5, 5.5);
            Location to = server.at(5.5, 5, 5.5);
            to.setYaw(180);
            server.server.getPluginManager().callEvent(new PlayerMoveEvent(player, from, to));
            assertTrue(enters.isEmpty(), "the tracker would have seen an entry: it was never asked");

            server.server.getPluginManager().callEvent(step(server, player, 5.5, 6.5));
            assertEquals(1, enters.size(), "a step into the next block is asked, and enters the plaza it never saw");
        }
    }

    @Test
    void aDeniedTeleportIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyTeleportInFlag()));
            server.listen(new RegionMoveListener());
            Player player = server.player("jumper", 20.5, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));

            PlayerTeleportEvent in = server.call(new PlayerTeleportEvent(player, server.at(20.5, 5, 5.5), server.at(5.5, 5, 5.5),
                    PlayerTeleportEvent.TeleportCause.COMMAND));
            PlayerTeleportEvent elsewhere = server.call(new PlayerTeleportEvent(player, server.at(20.5, 5, 5.5), server.at(30.5, 5, 5.5),
                    PlayerTeleportEvent.TeleportCause.COMMAND));

            assertTrue(in.isCancelled());
            assertEquals(5.5, in.getTo().getX(), "cancelled, not redirected");
            assertFalse(elsewhere.isCancelled());
        }
    }

    @Test
    void aVehicleCarryingThePlayerIsEjectedAndSentBack() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            arena(server);
            server.listen(new RegionMoveListener());
            Player rider = server.player("rider", 12.7, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(rider, "joined"));
            Cart cart = new Cart(server, rider);

            server.server.getPluginManager().callEvent(new VehicleMoveEvent(cart.vehicle, server.at(12.7, 5, 5.5), server.at(10.5, 5, 5.5)));

            assertEquals(1, cart.ejected.get());
            assertEquals(12.5, cart.sentTo.get().getX(), "the cart goes back to the last valid block");
            assertEquals(12.5, rider.getLocation().getX(), "and so does its rider");
        }
    }

    @Test
    void theSecondPassengerAlsoDecides() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            arena(server);
            server.listen(new RegionMoveListener());
            Player staff = server.player("staff", 12.7, 5, 5.5, "evernifecore.region.bypass.*");
            Player guest = server.player("guest", 12.7, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(staff, "joined"));
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(guest, "joined"));
            Cart cart = new Cart(server, staff, guest);

            server.server.getPluginManager().callEvent(new VehicleMoveEvent(cart.vehicle, server.at(12.7, 5, 5.5), server.at(10.5, 5, 5.5)));

            assertEquals(1, cart.ejected.get(), "the first passenger may enter, the second may not: the cart turns back");
            assertEquals(12.5, staff.getLocation().getX(), "every passenger leaves with it");
            assertEquals(12.5, guest.getLocation().getX());
        }
    }

    @Test
    void joiningInsideEntersWithoutCancelling() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region arena = arena(server);
            server.listen(new RegionMoveListener());
            List<RegionEnterEvent> enters = server.regions.recorded(RegionEnterEvent.class);
            Player player = server.player("camper", 5.5, 5, 5.5);

            server.server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));

            assertEquals(1, enters.size(), "deny-entry does not keep out someone who appears inside");
            assertEquals(arena, enters.get(0).getRegion());
            assertEquals(MoveCause.JOIN, enters.get(0).getCause());
        }
    }

    @Test
    void quittingLeavesEveryRegion() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region arena = arena(server);
            server.listen(new RegionMoveListener());
            List<RegionLeaveEvent> leaves = server.regions.recorded(RegionLeaveEvent.class);
            Player player = server.player("leaver", 5.5, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));
            assertTrue(leaves.isEmpty());

            server.server.getPluginManager().callEvent(new PlayerQuitEvent(player, "left"));

            assertEquals(1, leaves.size(), "the player had no PlayerData, and still leaves");
            assertEquals(arena, leaves.get(0).getRegion());
            assertEquals(MoveCause.QUIT, leaves.get(0).getCause());
        }
    }

    @Test
    void respawnAndWorldChangeReconcileTheSession() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region arena = arena(server);
            server.listen(new RegionMoveListener());
            List<RegionBoundaryEvent> crossings = server.regions.recorded(RegionBoundaryEvent.class);
            Player player = server.player("phoenix", 20.5, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));

            server.server.getPluginManager().callEvent(new PlayerRespawnEvent(player, server.at(5.5, 5, 5.5), false));

            assertEquals(1, crossings.size(), "a respawn inside is not denied by deny-entry: it reconciles");
            assertEquals(MoveCause.RESPAWN, crossings.get(0).getCause());
            assertEquals(Collections.singletonList(arena),
                    RegionLookup.get().getSessionIfPresent(player.getUniqueId()).getCurrent(), "the session is inside");

            World nether = server.server.world("nether", 0, 256);
            Location there = new Location(nether, 5.5, 5, 5.5);
            server.server.getPluginManager().callEvent(new PlayerTeleportEvent(player, server.at(5.5, 5, 5.5), there, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL));
            player.teleport(there);
            crossings.clear();

            server.server.getPluginManager().callEvent(new PlayerChangedWorldEvent(player, server.world));

            assertTrue(crossings.isEmpty(), "the teleport already committed the change; the world change posts nothing more");
        }
    }

    @Test
    void theMoveListenerSitsOnTheServerOnlyWhileSomeRegionNeedsIt() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            McRegionListeners bridge = McRegionListeners.start(server.server.getPluginData());
            ECEventSubscription<RegionEnterEvent> subscription = null;
            try {
                assertFalse(isRegistered(PlayerMoveEvent.getHandlerList(), RegionMoveListener.class), "nothing needs MOVE yet");

                Region arena = arena(server);
                assertTrue(isRegistered(PlayerMoveEvent.getHandlerList(), RegionMoveListener.class));

                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                server.regions.getManager().remove(arena);
                server.regions.runDue();
                assertFalse(isRegistered(PlayerMoveEvent.getHandlerList(), RegionMoveListener.class), "the region that needed it left");

                subscription = ECEventBus.global().subscribe(RegionEnterEvent.class, event -> {
                });
                assertTrue(isRegistered(PlayerMoveEvent.getHandlerList(), RegionMoveListener.class),
                        "someone listening to region entries needs MOVE too, with no region at all");
            } finally {
                if (subscription != null) {
                    subscription.unsubscribe();
                }
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    @Test
    void onlinePlayersAreSeededWhenTheMoveListenerArrives() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player online = server.player("resident", 5.5, 5, 5.5);
            McRegionListeners bridge = McRegionListeners.start(server.server.getPluginData());
            try {
                arena(server);   //the first region that needs MOVE: the listener arrives, and seeds whoever is online
                Player unseen = server.player("stranger", 5.5, 5, 5.5);   //logged in after the seeding, and never joined

                PlayerMoveEvent resident = server.call(step(server, online, 5.5, 6.5));
                PlayerMoveEvent stranger = server.call(step(server, unseen, 5.5, 6.5));

                assertFalse(denied(resident, 6.5), "already inside when the listener came: a step is not an entry");
                assertTrue(denied(stranger, 6.5), "nobody seeded this one, so the same step reads as an entry");
            } finally {
                bridge.stop();
            }
        }
    }
}
