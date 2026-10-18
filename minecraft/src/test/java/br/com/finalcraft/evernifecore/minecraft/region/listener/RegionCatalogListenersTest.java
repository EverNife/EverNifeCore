package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The listeners of the respawn, fire-spread, item and vehicle sources, each against the flag that asks for it. */
class RegionCatalogListenersTest {

    @TempDirNobodyCleans
    Path tempDir;

    private static BlockSpreadEvent fireSpreadsTo(RegionServer server, int x) {
        BlockState fire = Doubles.of(BlockState.class).returning("getType", Material.FIRE).build();
        return new BlockSpreadEvent(server.world.getBlockAt(x, 5, 5), server.world.getBlockAt(x - 1, 5, 5), fire);
    }

    @Test
    void denyFireSpreadStopsSpreadingFireAndDenyBlockPlaceNoLongerDoes() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.flag(CoreFlags.DENY_BLOCK_PLACE);
            server.box("forest", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_FIRE_SPREAD));
            server.box("plots", 20, 0, 0, 30, 10, 10, CoreFlags.DENY_BLOCK_PLACE);
            server.listen(new RegionFireSpreadListener());
            server.listen(new RegionBlockListener(EventSource.PLACE));

            assertTrue(server.call(fireSpreadsTo(server, 5)).isCancelled());
            assertTrue(server.call(new BlockIgniteEvent(server.world.getBlockAt(5, 5, 5), BlockIgniteEvent.IgniteCause.SPREAD, (Entity) null))
                    .isCancelled(), "the ignite half of the same spread");
            assertFalse(server.call(fireSpreadsTo(server, 25)).isCancelled(), "deny-block-place alone lets fire spread");
            assertFalse(server.call(new BlockIgniteEvent(server.world.getBlockAt(25, 5, 5), BlockIgniteEvent.IgniteCause.SPREAD, (Entity) null))
                    .isCancelled());
        }
    }

    @Test
    void fireLitByTheEnvironmentIsDeniedByDenyBlockPlaceEnvironment() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.flag(CoreFlags.DENY_BLOCK_PLACE);
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_PLACE_ENVIRONMENT));
            server.listen(new RegionBlockListener(EventSource.PLACE));
            Player player = server.player("pyro", 5, 5, 5);

            assertTrue(server.call(new BlockIgniteEvent(server.world.getBlockAt(5, 5, 5), BlockIgniteEvent.IgniteCause.LAVA, (Entity) null))
                    .isCancelled());
            assertFalse(server.call(new BlockIgniteEvent(server.world.getBlockAt(5, 5, 5), BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player))
                    .isCancelled(), "a player is not the environment");
        }
    }

    @Test
    void dyingInsideRespawnsAtTheSpawnPointAheadOfTheBedAndOutsideAtTheBed() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region arena = server.box("arena", 0, 0, 0, 10, 10, 10);
            arena.setFlag(server.flag(CoreFlags.SPAWN_POINT), WorldLocPos.of(3, 64, 3, RegionServer.WORLD));
            server.listen(new RegionRespawnListener());
            Location bed = server.at(100, 64, 100);

            PlayerRespawnEvent inside = server.call(new PlayerRespawnEvent(server.player("inside", 5, 5, 5), bed.clone(), true));
            assertEquals(server.at(3, 64, 3), inside.getRespawnLocation());

            PlayerRespawnEvent outside = server.call(new PlayerRespawnEvent(server.player("outside", 50, 5, 5), bed.clone(), true));
            assertEquals(bed, outside.getRespawnLocation(), "died outside: the bed keeps it");
        }
    }

    private static Item item(RegionServer server, Material type, double x) {
        return RegionServer.entity(Item.class, EntityType.ITEM, server.at(x, 5, 5)).returning("getItemStack", new ItemStack(type)).build();
    }

    @Test
    void denyItemDropAndDenyItemPickupCancelTheirOwnEventOnly() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("market", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_ITEM_DROP));
            server.box("vault", 20, 0, 0, 30, 10, 10, server.flag(CoreFlags.DENY_ITEM_PICKUP));
            server.listen(new RegionItemListeners.Drop());
            server.listen(new RegionItemListeners.Pickup());
            Player trader = server.player("trader", 5, 5, 5);

            assertTrue(server.call(new PlayerDropItemEvent(trader, item(server, Material.DIAMOND, 5))).isCancelled());
            assertFalse(server.call(new EntityPickupItemEvent(trader, item(server, Material.DIAMOND, 5), 0)).isCancelled(),
                    "the market only denies drops");
            assertTrue(server.call(new EntityPickupItemEvent(trader, item(server, Material.DIAMOND, 25), 0)).isCancelled());
            assertFalse(server.call(new PlayerDropItemEvent(trader, item(server, Material.DIAMOND, 50))).isCancelled(), "outside every region");
        }
    }

    @Test
    void denyVehicleEnterLeavesBreakingToDenyVehicle() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region rails = server.box("rails", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_VEHICLE));
            rails.setFlag(server.flag(CoreFlags.DENY_VEHICLE_ENTER), Tristate.FALSE);
            server.listen(new RegionVehicleListeners.BreakAndEnter());
            Player rider = server.player("rider", 5, 5, 5);
            Minecart cart = RegionServer.entity(Minecart.class, EntityType.MINECART, server.at(5, 5, 5)).build();

            assertFalse(server.call(new VehicleEnterEvent(cart, rider)).isCancelled(), "deny-vehicle-enter FALSE lets them in");
            VehicleDestroyEvent destroy = server.call(new VehicleDestroyEvent(cart, rider));
            assertTrue(destroy.isCancelled(), "breaking reads deny-vehicle");
            assertSame(rider, destroy.getAttacker());
        }
    }

    @Test
    void aVehicleItemIsAMinecartOrABoatOfAnyEra() {
        assertTrue(RegionVehicleListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.MINECART)));
        assertTrue(RegionVehicleListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.HOPPER_MINECART)));
        assertTrue(RegionVehicleListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.OAK_BOAT)));
        assertFalse(RegionVehicleListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.STONE)));
        assertFalse(RegionVehicleListeners.LegacyPlace.isVehicleItem(null));
    }
}
