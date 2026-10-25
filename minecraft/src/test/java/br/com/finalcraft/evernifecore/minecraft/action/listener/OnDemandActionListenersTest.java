package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.DamageActionEvent;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.action.event.SpawnActionEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld.isRegistered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The action listeners are hooked on the hottest native events there are, and with no region asking they exist only for
 * whoever listens to the action events. So the server carries each one exactly while somebody does - on the bus or as a
 * Bukkit listener the bus cannot see by itself - and an event nobody listens to is never built.
 */
class OnDemandActionListenersTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aBukkitListenerOnBreakPostIsWhatPutsTheBreakListenerOnTheServer() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActionListeners bridge = ActionListeners.start(server.server.getPluginData());
            Listener onBreak = new Listener() {
            };
            try {
                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), BlockActionListener.class), "nobody asks for BREAK");

                server.server.getPluginManager().registerEvent(BreakActionEvent.Post.class, onBreak, EventPriority.NORMAL,
                        (ignoredListener, event) -> {
                        }, server.server.getPlugin());

                assertTrue(ECEventBus.global().hasListeners(BreakActionEvent.Post.class), "a Bukkit registration is a listener too");
                assertTrue(isRegistered(BlockBreakEvent.getHandlerList(), BlockActionListener.class),
                        "and that is what registers the break listener, with no region at all");

                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                HandlerList.unregisterAll(onBreak);
                server.regions.runDue();

                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), BlockActionListener.class), "the last one asking left");
            } finally {
                HandlerList.unregisterAll(onBreak);
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    @Test
    void aBusSubscriberOfDamagePostIsWhatPutsTheDamageListenerOnTheServer() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActionListeners bridge = ActionListeners.start(server.server.getPluginData());
            ECEventSubscription<DamageActionEvent.Post> subscription = null;
            try {
                assertFalse(isRegistered(EntityDamageByEntityEvent.getHandlerList(), DamageActionListener.class));

                subscription = ECEventBus.global().subscribe(DamageActionEvent.Post.class, event -> {
                });
                assertTrue(isRegistered(EntityDamageByEntityEvent.getHandlerList(), DamageActionListener.class));
                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), BlockActionListener.class), "only the source subscribed to");

                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                subscription.unsubscribe();
                server.regions.runDue();

                assertFalse(isRegistered(EntityDamageByEntityEvent.getHandlerList(), DamageActionListener.class));
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
    void anEventNobodyListensToIsNeverBuilt() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActionListeners bridge = ActionListeners.start(server.server.getPluginData());
            AtomicInteger stacksRead = new AtomicInteger();
            Item item = RegionServer.entity(Item.class, EntityType.ITEM, server.at(50, 5, 5))
                    .on("getItemStack", args -> {
                        stacksRead.incrementAndGet();
                        return new ItemStack(Material.DIAMOND);
                    }).build();
            Player trader = server.player("trader", 50, 5, 5);
            ECEventSubscription<DropActionEvent.Pre> pre = ECEventBus.global().subscribe(DropActionEvent.Pre.class, event -> {
            });
            try {
                server.call(new PlayerDropItemEvent(trader, item));
                assertEquals(1, stacksRead.get(), "the Pre was built for its subscriber; the Post, which nobody hears, never was");

                List<DropActionEvent.Post> posts = new ArrayList<>();
                ECEventSubscription<DropActionEvent.Post> post = ECEventBus.global().subscribe(DropActionEvent.Post.class, posts::add);
                server.call(new PlayerDropItemEvent(trader, item));
                post.unsubscribe();

                assertEquals(3, stacksRead.get(), "now both halves were built");
                assertEquals(1, posts.size());
            } finally {
                pre.unsubscribe();
                bridge.stop();
            }
        }
    }

    @Test
    void aCancelledPreCancelsTheNativeEvent() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActionListeners bridge = ActionListeners.start(server.server.getPluginData());
            ECEventSubscription<BreakActionEvent.Pre> veto = ECEventBus.global().subscribe(BreakActionEvent.Pre.class,
                    event -> event.setCancelled(true));
            try {
                Player player = server.player("miner", 50, 5, 5);

                assertTrue(server.call(new BlockBreakEvent(server.world.getBlockAt(50, 5, 5), player)).isCancelled(),
                        "no region denied it: the subscriber of the Pre did");
            } finally {
                veto.unsubscribe();
                bridge.stop();
            }
        }
    }

    @Test
    void aCancelledNativeEventGetsNoPost() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK));
            ActionListeners bridge = ActionListeners.start(server.server.getPluginData());
            List<BreakActionEvent.Post> posts = new ArrayList<>();
            ECEventSubscription<BreakActionEvent.Post> post = ECEventBus.global().subscribe(BreakActionEvent.Post.class, posts::add);
            try {
                Player player = server.player("miner", 5, 5, 5);

                assertTrue(server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player)).isCancelled());
                assertTrue(posts.isEmpty(), "the regions denied it: no Post");

                assertFalse(server.call(new BlockBreakEvent(server.world.getBlockAt(50, 5, 5), player)).isCancelled());
                assertEquals(1, posts.size(), "outside the vault the break went through and was posted");
                assertEquals(EventSource.BREAK, posts.get(0).getSource());
                assertEquals(50, (int) posts.get(0).getContext().getPosition().getX());
            } finally {
                post.unsubscribe();
                bridge.stop();
            }
        }
    }

    @Test
    void anEggSpawnIsPostedThoughTheRegionsNeverDecideIt() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("farm", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_SPAWN));
            ActionListeners bridge = ActionListeners.start(server.server.getPluginData());
            List<SpawnActionEvent.Post> posts = new ArrayList<>();
            ECEventSubscription<SpawnActionEvent.Post> post = ECEventBus.global().subscribe(SpawnActionEvent.Post.class, posts::add);
            try {
                Zombie zombie = RegionServer.entity(Zombie.class, EntityType.ZOMBIE, server.at(5, 5, 5)).build();

                assertFalse(server.call(new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)).isCancelled(),
                        "an egg is a player acting: deny-spawn never decides it");
                assertTrue(server.call(new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.NATURAL)).isCancelled());

                assertEquals(1, posts.size(), "the egg spawn went through and was posted; the denied one was not");
                assertEquals("SPAWNER_EGG", posts.get(0).getContext().getReason());
            } finally {
                post.unsubscribe();
                bridge.stop();
            }
        }
    }
}
