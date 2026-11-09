package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
import java.util.ArrayList;
import java.util.List;

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

    /** Five diamonds on an entity that keeps what {@code setItemStack} gives it, as the real one does. */
    private static Item fiveDiamonds(RegionServer server, double x) {
        ItemStack[] held = {new ItemStack(Material.DIAMOND, 5)};
        return RegionServer.entity(Item.class, EntityType.ITEM, server.at(x, 5, 5))
                .on("getItemStack", args -> held[0])
                .on("setItemStack", args -> {
                    held[0] = (ItemStack) args[0];
                    return null;
                }).build();
    }

    /** Another plugin that cancels every drop before the core's listener. */
    public static final class CancelsAtLowest implements ECListener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onDrop(PlayerDropItemEvent event) {
            event.setCancelled(true);
        }
    }

    /** Another plugin that gives every cancelled drop a second chance after the core's listener. */
    public static final class UncancelsAtNormal implements ECListener {
        @EventHandler(priority = EventPriority.NORMAL)
        public void onDrop(PlayerDropItemEvent event) {
            event.setCancelled(false);
        }
    }

    /** Runs {@code body} hearing every {@code DropActionEvent.Post}, each described when it is heard, with what the entity held then. */
    private static List<String> postsDuring(Item entity, Runnable body) {
        List<String> posts = new ArrayList<>();
        ECEventSubscription<DropActionEvent.Post> heard = ECEventBus.global().subscribe(DropActionEvent.Post.class, post -> {
            //counted before it is read: the bus swallows a subscriber's exception, and a Post of an empty entity cannot be read
            int index = posts.size();
            posts.add("unreadable");
            posts.set(index, post.getContext().getItemKey() + " x" + post.getContext().getAmount() + " spec x" + post.getContext().getItem().getAmount()
                    + " entity " + entity.getItemStack().getType());
        });
        try {
            body.run();
        } finally {
            heard.unsubscribe();
        }
        return posts;
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
    void aDropMarkedForDestructionEndsCancelledAndEmptyWithAPostOfTheRealItem() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS));
            server.listen(new ItemActionListeners.Drop());
            Player fighter = server.player("fighter", 5, 5, 5);
            Item inside = fiveDiamonds(server, 5);
            Item outside = fiveDiamonds(server, 50);
            boolean[] cancelled = new boolean[2];

            List<String> posts = postsDuring(inside, () -> cancelled[0] = server.call(new PlayerDropItemEvent(fighter, inside)).isCancelled());
            List<String> postsOutside = postsDuring(outside, () -> cancelled[1] = server.call(new PlayerDropItemEvent(fighter, outside)).isCancelled());

            assertTrue(cancelled[0], "a cancelled drop never enters the world");
            assertEquals(Material.AIR, inside.getItemStack().getType(), "the server gives the entity's stack back: nothing comes back");
            assertEquals(1, posts.size());
            assertEquals("minecraft:diamond x5 spec x5 entity DIAMOND", posts.get(0), "the Post is asked before the entity is emptied");
            assertFalse(cancelled[1], "outside the region nothing changes");
            assertEquals(Material.DIAMOND, outside.getItemStack().getType());
            assertEquals(1, postsOutside.size());
        }
    }

    @Test
    void denyItemDropBeatsDestructionTheEntityKeepsTheItemAndNoPostGoesOut() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS), server.flag(CoreFlags.DENY_ITEM_DROP));
            server.listen(new ItemActionListeners.Drop());
            Player fighter = server.player("fighter", 5, 5, 5);
            Item item = fiveDiamonds(server, 5);
            boolean[] cancelled = new boolean[1];

            List<String> posts = postsDuring(item, () -> cancelled[0] = server.call(new PlayerDropItemEvent(fighter, item)).isCancelled());

            assertTrue(cancelled[0]);
            assertEquals(Material.DIAMOND, item.getItemStack().getType(), "the server gives these diamonds back");
            assertTrue(posts.isEmpty());
        }
    }

    @Test
    void aDropAnotherPluginCancelsAtLowestKeepsItsItemUnderDestroyDroppedItems() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS));
            server.listen(new CancelsAtLowest());
            server.listen(new ItemActionListeners.Drop());
            Player fighter = server.player("fighter", 5, 5, 5);
            Item item = fiveDiamonds(server, 5);

            List<String> posts = postsDuring(item, () -> server.call(new PlayerDropItemEvent(fighter, item)));

            assertEquals(Material.DIAMOND, item.getItemStack().getType(), "onDrop never ran: the server gives these diamonds back");
            assertTrue(posts.isEmpty());
        }
    }

    @Test
    void aPluginThatUncancelsADestroyedDropGetsNoSecondPost() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new UncancelsAtNormal());
            Player fighter = server.player("fighter", 5, 5, 5);
            Item item = fiveDiamonds(server, 5);
            boolean[] cancelled = new boolean[1];

            List<String> posts = postsDuring(item, () -> cancelled[0] = server.call(new PlayerDropItemEvent(fighter, item)).isCancelled());

            assertFalse(cancelled[0], "afterDrop ran");
            assertEquals(Material.AIR, item.getItemStack().getType(), "the item is gone either way");
            assertEquals(1, posts.size(), "the Post of this drop went out at LOW");
        }
    }

    @Test
    void aDestroyedItemKeepsNoOwner() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DESTROY_DROPPED_ITEMS), server.flag(CoreFlags.OWNER_ONLY_PICKUP));
            server.listen(new ItemActionListeners.Drop());
            server.listen(new ItemActionListeners.Pickup());
            Item item = fiveDiamonds(server, 5);

            server.call(new PlayerDropItemEvent(server.player("owner", 5, 5, 5), item));

            assertFalse(server.call(new EntityPickupItemEvent(server.player("other", 6, 5, 5), item, 0)).isCancelled(), "no owner was kept for it");
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
