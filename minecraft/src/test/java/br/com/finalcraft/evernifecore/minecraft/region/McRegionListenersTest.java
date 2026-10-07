package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyBlockBreakFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyBlockPlaceFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyInteractFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyMonsterSpawnFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyPvpFlag;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld.isRegistered;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each region listener is on the server exactly while some region needs its source, and none hears a disabled world. */
class McRegionListenersTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aListenerSitsOnTheServerOnlyWhileSomeRegionNeedsItsSource() {
        int idleTicks = EventSourceUsage.get().getIdleTicks();
        try (RegionServer server = RegionServer.open(tempDir)) {
            McRegionListeners bridge = McRegionListeners.start(server.server.getPluginData());
            try {
                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class), "no region needs BREAK yet");

                Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyBlockBreakFlag(server.regions.getOwner())));
                assertTrue(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class));

                EventSourceUsage.get().setIdleTicks(0);
                server.regions.getManager().remove(vault);
                server.regions.runDue();

                assertFalse(isRegistered(BlockBreakEvent.getHandlerList(), RegionBlockListener.class), "the last region that needed it left");
            } finally {
                bridge.stop();
            }
        } finally {
            EventSourceUsage.get().setIdleTicks(idleTicks);
        }
    }

    @Test
    void aDisabledWorldSkipsEveryHandler() {
        List<String> before = ECSettings.REGIONS_DISABLED_WORLDS;
        try (RegionServer server = RegionServer.open(tempDir)) {
            ECPluginData owner = server.regions.getOwner();
            server.box("plots", 0, 0, 0, 10, 10, 10,
                    server.flag(new DenyBlockBreakFlag(owner)), server.flag(new DenyBlockPlaceFlag(owner)),
                    server.flag(new DenyInteractFlag(owner)), server.flag(new DenyPvpFlag(owner)), server.flag(new DenyMonsterSpawnFlag(owner)));
            McRegionListeners bridge = McRegionListeners.start(server.server.getPluginData());
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
}
