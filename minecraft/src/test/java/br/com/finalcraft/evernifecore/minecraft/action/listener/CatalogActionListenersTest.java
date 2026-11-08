package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
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
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The listeners of the respawn, fire-spread, item and vehicle sources, each against the flag that asks for it. */
class CatalogActionListenersTest {

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
            server.listen(new FireSpreadActionListener());
            server.listen(new BlockActionListener(EventSource.PLACE));

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
            server.listen(new BlockActionListener(EventSource.PLACE));
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
            server.listen(new RespawnActionListener());
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

    private static Item removableItem(RegionServer server, double x, Runnable onRemove) {
        return RegionServer.entity(Item.class, EntityType.ITEM, server.at(x, 5, 5)).returning("getItemStack", new ItemStack(Material.DIAMOND))
                .on("remove", args -> {
                    onRemove.run();
                    return null;
                }).build();
    }

    @Test
    void denyItemDropAndDenyItemPickupCancelTheirOwnEventOnly() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("market", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_ITEM_DROP));
            server.box("vault", 20, 0, 0, 30, 10, 10, server.flag(CoreFlags.DENY_ITEM_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.Pickup());
            Player trader = server.player("trader", 5, 5, 5);

            assertTrue(server.call(new PlayerDropItemEvent(trader, item(server, Material.DIAMOND, 5))).isCancelled());
            assertFalse(server.call(new EntityPickupItemEvent(trader, item(server, Material.DIAMOND, 5), 0)).isCancelled(),
                    "the market only denies drops");
            assertTrue(server.call(new EntityPickupItemEvent(trader, item(server, Material.DIAMOND, 25), 0)).isCancelled());
            assertFalse(server.call(new PlayerDropItemEvent(trader, item(server, Material.DIAMOND, 50))).isCancelled(), "outside every region");
        }
    }

    @Test
    void destroyDroppedItemsLetsTheDropThroughAndRemovesItsItemATickLater() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS));
            server.listen(new ItemActionListeners.Drop());
            Player fighter = server.player("fighter", 5, 5, 5);
            AtomicInteger removed = new AtomicInteger();
            Item inside = removableItem(server, 5, removed::incrementAndGet);
            Item outside = removableItem(server, 50, () -> removed.addAndGet(100));

            assertFalse(server.call(new PlayerDropItemEvent(fighter, inside)).isCancelled(), "the drop itself is allowed");
            assertFalse(server.call(new PlayerDropItemEvent(fighter, outside)).isCancelled());
            assertEquals(0, removed.get(), "the server has not added the item yet: nothing is removed during the event");

            server.server.runScheduledTasks();

            assertEquals(1, removed.get(), "only the item dropped inside vanishes");
        }
    }

    @Test
    void aDropAnotherPluginCancelsKeepsItsItemUnderDestroyDroppedItems() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS));
            server.listen(new ItemActionListeners.Drop());
            Player fighter = server.player("fighter", 5, 5, 5);
            AtomicInteger removed = new AtomicInteger();
            Item item = removableItem(server, 5, removed::incrementAndGet);

            PlayerDropItemEvent drop = server.call(new PlayerDropItemEvent(fighter, item));
            drop.setCancelled(true); //a later listener's word: the server gives the item back
            server.server.runScheduledTasks();

            assertEquals(0, removed.get());
        }
    }

    @Test
    void ownerOnlyPickupGivesTheItemBackToItsDropperAndNobodyElse() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.OWNER_ONLY_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.Pickup());
            Player owner = server.player("owner", 5, 5, 5);
            Player other = server.player("other", 6, 5, 5);
            Item dropped = item(server, Material.DIAMOND, 5);

            assertFalse(server.call(new PlayerDropItemEvent(owner, dropped)).isCancelled(), "the drop itself is allowed");

            assertTrue(server.call(new EntityPickupItemEvent(other, dropped, 0)).isCancelled());
            assertFalse(server.call(new EntityPickupItemEvent(owner, dropped, 0)).isCancelled());
            assertFalse(server.call(new EntityPickupItemEvent(other, item(server, Material.DIAMOND, 5), 0)).isCancelled(), "nobody dropped this one");

            Item droppedOutside = item(server, Material.DIAMOND, 50);
            server.call(new PlayerDropItemEvent(owner, droppedOutside));
            assertFalse(server.call(new EntityPickupItemEvent(other, droppedOutside, 0)).isCancelled(), "dropped outside the region: nobody's");
        }
    }

    @Test
    void anOwnedItemThatRolledOutOfTheRegionIsAnyonesThere() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.OWNER_ONLY_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.Pickup());
            Location[] lying = {server.at(5, 5, 5)};
            Item dropped = RegionServer.entity(Item.class, EntityType.ITEM, lying[0]).returning("getItemStack", new ItemStack(Material.DIAMOND))
                    .on("getLocation", args -> lying[0]).build();
            server.call(new PlayerDropItemEvent(server.player("owner", 5, 5, 5), dropped));
            Player other = server.player("other", 50, 5, 5);
            assertTrue(server.call(new EntityPickupItemEvent(other, dropped, 0)).isCancelled(), "inside: only the owner");

            lying[0] = server.at(50, 5, 5);

            assertFalse(server.call(new EntityPickupItemEvent(other, dropped, 0)).isCancelled(), "the region decides where the item lies now");
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void ownerOnlyPickupHoldsOnThePickupEventOfTheOldServersToo() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.OWNER_ONLY_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.LegacyPickup());
            Player owner = server.player("owner", 5, 5, 5);
            Player other = server.player("other", 6, 5, 5);
            Item dropped = item(server, Material.DIAMOND, 5);

            server.call(new PlayerDropItemEvent(owner, dropped));

            assertTrue(server.call(new PlayerPickupItemEvent(other, dropped, 0)).isCancelled());
            assertFalse(server.call(new PlayerPickupItemEvent(owner, dropped, 0)).isCancelled());
        }
    }

    @Test
    void aHopperDoesNotPullAnOwnedItemInsideTheRegion() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.OWNER_ONLY_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.HopperPickup());
            Player owner = server.player("owner", 5, 5, 5);
            Inventory hopper = Doubles.of(Inventory.class).build();
            Item dropped = item(server, Material.DIAMOND, 5);
            server.call(new PlayerDropItemEvent(owner, dropped));

            assertTrue(server.call(new InventoryPickupItemEvent(hopper, dropped)).isCancelled());
            assertFalse(server.call(new InventoryPickupItemEvent(hopper, item(server, Material.DIAMOND, 5))).isCancelled(), "nobody dropped this one");
        }
    }

    @Test
    void pilesOfDifferentOwnersDoNotMerge() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.OWNER_ONLY_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.Merge());
            Item first = item(server, Material.DIAMOND, 5);
            Item second = item(server, Material.DIAMOND, 5);
            Item third = item(server, Material.DIAMOND, 5);
            Item nobodys = item(server, Material.DIAMOND, 5);
            server.call(new PlayerDropItemEvent(server.player("owner", 5, 5, 5), first));
            server.call(new PlayerDropItemEvent(server.player("owner", 5, 5, 5), second));
            server.call(new PlayerDropItemEvent(server.player("other", 6, 5, 5), third));

            assertFalse(server.call(new ItemMergeEvent(first, second)).isCancelled(), "same owner");
            assertTrue(server.call(new ItemMergeEvent(first, third)).isCancelled());
            assertTrue(server.call(new ItemMergeEvent(first, nobodys)).isCancelled(), "the owned pile would lose its owner, or the free one gain it");
        }
    }

    @Test
    void denyVehicleEnterLeavesBreakingToDenyVehicle() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region rails = server.box("rails", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_VEHICLE));
            rails.setFlag(server.flag(CoreFlags.DENY_VEHICLE_ENTER), Tristate.FALSE);
            server.listen(new VehicleActionListeners.BreakAndEnter());
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
        assertTrue(VehicleActionListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.MINECART)));
        assertTrue(VehicleActionListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.HOPPER_MINECART)));
        assertTrue(VehicleActionListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.OAK_BOAT)));
        assertFalse(VehicleActionListeners.LegacyPlace.isVehicleItem(new ItemStack(Material.STONE)));
        assertFalse(VehicleActionListeners.LegacyPlace.isVehicleItem(null));
    }
}
