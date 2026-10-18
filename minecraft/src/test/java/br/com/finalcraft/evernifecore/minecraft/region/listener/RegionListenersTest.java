package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DenyBlockBreakFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyBlockPlaceFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyPvpFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenySettings;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld.isRegistered;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each region listener is on the server exactly while some region needs its source, and none hears a disabled world. */
class RegionListenersTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aListenerSitsOnTheServerOnlyWhileSomeRegionNeedsItsSource() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
            try {
                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class), "no region needs BREAK yet");

                Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyBlockBreakFlag()));
                assertTrue(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class));

                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                server.regions.getManager().remove(vault);
                server.regions.runDue();

                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class), "the last region that needed it left");
            } finally {
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    /** A plugin's own listener, standing in for the one that raises its duel hits. */
    public static final class DuelHitListener implements ECListener {
        @EventHandler
        public void onInteract(PlayerInteractEvent event) {
        }
    }

    @Test
    void aPluginsOwnListenerFollowsItsOwnSource() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            //the server's plugin plays the duel plugin: a listener registers in the name of an enabled plugin
            ECPluginData duel = server.server.getPluginData();
            EventSource duelHit = EventSource.register(duel, "duel-hit");
            DenyFlag denyDuelHit = new DenyFlag(FlagKey.of(duel, "deny-duel-hit"), duelHit) {
            };
            FlagRegistry.get().register(duel, denyDuelHit);
            DuelHitListener listener = new DuelHitListener();
            UsageWatch watch = RegionListeners.follow(duel, duelHit, listener, null);
            try {
                assertFalse(isRegistered(PlayerInteractEvent.getHandlerList(), DuelHitListener.class), "no region needs the duel hit yet");

                Region arena = server.box("arena", 0, 0, 0, 10, 10, 10, denyDuelHit);
                assertTrue(isRegistered(PlayerInteractEvent.getHandlerList(), DuelHitListener.class));

                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                server.regions.getManager().remove(arena);
                server.regions.runDue();

                assertFalse(isRegistered(PlayerInteractEvent.getHandlerList(), DuelHitListener.class), "the last region that needed it left");
            } finally {
                watch.stop();
                listener.unregisterThis();
                FlagRegistry.get().unregisterAll(duel);
                EventSource.unregisterAll(duel);
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    @Test
    void aDisabledWorldSkipsEveryHandler() {
        List<String> before = ECSettings.REGIONS_DISABLED_WORLDS;
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("plots", 0, 0, 0, 10, 10, 10,
                    server.flag(new DenyBlockBreakFlag()), server.flag(new DenyBlockPlaceFlag()),
                    server.flag(new DenyInteractFlag()), server.flag(new DenyPvpFlag()), server.flag(CoreFlags.DENY_SPAWN));
            RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
            try {
                Player player = server.player("plotter", 5, 5, 5);
                assertTrue(server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player)).isCancelled(),
                        "the world is protected before it is disabled");

                ECSettings.REGIONS_DISABLED_WORLDS = Collections.singletonList(RegionServer.WORLD);

                assertFalse(server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player)).isCancelled());
                assertFalse(server.call(new BlockPlaceEvent(server.world.getBlockAt(5, 5, 5), null, server.world.getBlockAt(5, 4, 5),
                        null, player, true, EquipmentSlot.HAND)).isCancelled());
                assertFalse(server.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null,
                        server.world.getBlockAt(5, 5, 5), BlockFace.UP)).isCancelled());
                assertFalse(server.call(new EntityDamageByEntityEvent(server.player("attacker", 5, 5, 5), player,
                        EntityDamageEvent.DamageCause.ENTITY_ATTACK, Doubles.of(DamageSource.class).build(), 1.0)).isCancelled());
                Zombie zombie = RegionServer.entity(Zombie.class, EntityType.ZOMBIE, server.at(5, 5, 5)).build();
                assertFalse(server.call(new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.NATURAL)).isCancelled());
            } finally {
                bridge.stop();
            }
        } finally {
            ECSettings.REGIONS_DISABLED_WORLDS = before;
        }
    }

    @Test
    void theExplosionListenerIsOnlyOnWhileAnExplosionOrWhatItBreaksIsAsked() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
            try {
                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                Region plots = server.box("plots", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_PLACE));
                assertTrue(isRegistered(BlockPlaceEvent.getHandlerList(), RegionBlockListener.class), "PLACE is needed");
                assertFalse(isRegistered(EntityExplodeEvent.getHandlerList(), RegionExplosionListener.class), "nothing asks about an explosion");

                Region bunker = server.box("bunker", 20, 0, 0, 30, 10, 10, server.flag(CoreFlags.DENY_EXPLOSION));
                assertTrue(isRegistered(EntityExplodeEvent.getHandlerList(), RegionExplosionListener.class));
                assertTrue(isRegistered(BlockExplodeEvent.getHandlerList(), RegionBlockExplodeListener.class));

                server.regions.getManager().remove(bunker);
                server.regions.runDue();
                assertFalse(isRegistered(EntityExplodeEvent.getHandlerList(), RegionExplosionListener.class), "the last region that asked left");

                //deny-block-break alone holds a creeper: what an explosion breaks is a BREAK
                Region vault = server.box("vault", 40, 0, 0, 50, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK));
                assertTrue(isRegistered(EntityExplodeEvent.getHandlerList(), RegionExplosionListener.class));
                server.regions.getManager().remove(vault);
                server.regions.runDue();
                assertFalse(isRegistered(EntityExplodeEvent.getHandlerList(), RegionExplosionListener.class));
                server.regions.getManager().remove(plots);
            } finally {
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    @Test
    void theFluidListenerIsOnOnlyForAFlagThatAsksForFluid() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
            try {
                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK),
                        server.flag(CoreFlags.DENY_BLOCK_PLACE), server.flag(CoreFlags.DENY_EXPLOSION));
                DenySettings environment = new DenySettings();
                environment.setActors(Collections.singletonList("environment"));
                vault.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, environment);

                assertTrue(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class), "BREAK is needed");
                assertFalse(isRegistered(BlockFromToEvent.getHandlerList(), RegionFluidListener.class),
                        "no flag of BREAK, PLACE or EXPLOSION - not even one about the environment - asks for a flow");

                DenyFlag denyFlow = new DenyFlag(FlagKey.of(server.regions.getOwner(), "deny-flow"), EventSource.FLUID) {
                };
                Region pond = server.box("pond", 20, 0, 0, 30, 10, 10, server.flag(denyFlow));
                assertTrue(isRegistered(BlockFromToEvent.getHandlerList(), RegionFluidListener.class));

                server.regions.getManager().remove(pond);
                server.regions.runDue();
                assertFalse(isRegistered(BlockFromToEvent.getHandlerList(), RegionFluidListener.class), "the flag that asked left with its region");
            } finally {
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    /** Each new source: off with no region asking, on with one, off again once that region left and the idle time ran out. */
    private static void assertFollows(RegionServer server, HandlerList handlers, Class<? extends ECListener> listener, RegionFlag<?> flag,
                                      Consumer<Region> define) {
        assertFalse(isRegistered(handlers, listener), listener.getSimpleName() + ": no region needs " + flag.getNeeds() + " yet");
        Region region = server.box("asks-" + flag.getKey().getName(), 0, 0, 0, 10, 10, 10);
        define.accept(region);
        assertTrue(isRegistered(handlers, listener), listener.getSimpleName() + " follows " + flag.getNeeds());
        server.regions.getManager().remove(region);
        server.regions.runDue();
        assertFalse(isRegistered(handlers, listener), listener.getSimpleName() + ": the last region that needed it left");
    }

    private static Consumer<Region> denying(TristateFlag flag) {
        return region -> region.setFlag(flag, Tristate.TRUE);
    }

    @Test
    void eachNewSourceHasItsListenerOnTheServerOnlyWhileSomeRegionNeedsIt() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        try (RegionServer server = RegionServer.open(tempDir)) {
            RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
            try {
                EventSourceUsage.get().setIdleTime(Duration.ZERO);
                server.flag(CoreFlags.DENY_FIRE_SPREAD);
                server.flag(CoreFlags.SPAWN_POINT);
                server.flag(CoreFlags.DENY_ITEM_DROP);
                server.flag(CoreFlags.DENY_ITEM_PICKUP);
                server.flag(CoreFlags.DENY_VEHICLE);

                assertFollows(server, BlockSpreadEvent.getHandlerList(), RegionFireSpreadListener.class, CoreFlags.DENY_FIRE_SPREAD,
                        denying(CoreFlags.DENY_FIRE_SPREAD));
                assertFollows(server, PlayerRespawnEvent.getHandlerList(), RegionRespawnListener.class, CoreFlags.SPAWN_POINT,
                        region -> region.setFlag(CoreFlags.SPAWN_POINT, WorldLocPos.of(1, 64, 1, RegionServer.WORLD)));
                assertFollows(server, PlayerDropItemEvent.getHandlerList(), RegionItemListeners.Drop.class, CoreFlags.DENY_ITEM_DROP,
                        denying(CoreFlags.DENY_ITEM_DROP));
                assertFollows(server, EntityPickupItemEvent.getHandlerList(), RegionItemListeners.Pickup.class, CoreFlags.DENY_ITEM_PICKUP,
                        denying(CoreFlags.DENY_ITEM_PICKUP));
                assertFollows(server, VehicleEnterEvent.getHandlerList(), RegionVehicleListeners.BreakAndEnter.class, CoreFlags.DENY_VEHICLE,
                        denying(CoreFlags.DENY_VEHICLE));
                assertFollows(server, EntityPlaceEvent.getHandlerList(), RegionVehicleListeners.Place.class, CoreFlags.DENY_VEHICLE,
                        denying(CoreFlags.DENY_VEHICLE));
            } finally {
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }

    @Test
    void aCheapFlagNeverPutsACostlyListenerOnTheServer() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
            try {
                server.box("market", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_ITEM_DROP));
                assertTrue(isRegistered(PlayerDropItemEvent.getHandlerList(), RegionItemListeners.Drop.class));
                assertFalse(isRegistered(EntityPickupItemEvent.getHandlerList(), RegionItemListeners.Pickup.class),
                        "deny-item-drop alone leaves the pickup listener off");

                server.box("plots", 20, 0, 0, 30, 10, 10, server.flag(CoreFlags.DENY_BLOCK_PLACE));
                assertTrue(isRegistered(BlockPlaceEvent.getHandlerList(), RegionBlockListener.class));
                assertFalse(isRegistered(BlockSpreadEvent.getHandlerList(), RegionFireSpreadListener.class),
                        "deny-block-place alone leaves the fire-spread listener off");
            } finally {
                bridge.stop();
            }
        }
    }
}
