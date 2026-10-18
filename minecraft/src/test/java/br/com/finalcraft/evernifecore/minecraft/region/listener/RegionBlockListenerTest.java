package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.core.DenySettings;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.core.DenyBlockBreakFlag;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A block broken or placed - by a player, a mob, fire or a piston - is asked of the regions where it is; a denial cancels the event. */
class RegionBlockListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** Never denies; counts how often it was asked. */
    static final class CountingFlag extends TristateFlag {
        final AtomicInteger asked = new AtomicInteger();

        CountingFlag(ECPluginData owner, EventSource source) {
            super(FlagKey.of(owner, "counting-" + source.getName()), Collections.singleton(source), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            asked.incrementAndGet();
            return Decision.PASS;
        }
    }

    private static BlockBreakEvent breakAt(RegionServer server, Player player, int x, int y, int z) {
        return new BlockBreakEvent(server.world.getBlockAt(x, y, z), player);
    }

    @Test
    void aBreakInsideDenyBlockBreakIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyBlockBreakFlag()));
            server.listen(new RegionBlockListener(EventSource.BREAK));
            Player player = server.player("miner", 20, 5, 20);

            assertTrue(server.call(breakAt(server, player, 5, 5, 5)).isCancelled());
            assertFalse(server.call(breakAt(server, player, 20, 5, 20)).isCancelled(), "outside the region the break happens");
        }
    }

    @Test
    void aPlaceOutsideEveryRegionPasses() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            CountingFlag counting = server.flag(new CountingFlag(server.regions.getOwner(), EventSource.PLACE));
            server.box("vault", 0, 0, 0, 10, 10, 10, counting);
            server.listen(new RegionBlockListener(EventSource.PLACE));
            Player player = server.player("builder", 20, 5, 20);

            BlockPlaceEvent outside = server.call(new BlockPlaceEvent(server.world.getBlockAt(20, 5, 20), null,
                    server.world.getBlockAt(20, 4, 20), null, player, true, EquipmentSlot.HAND));
            assertFalse(outside.isCancelled());
            assertEquals(0, counting.asked.get(), "no region there, so no flag is asked");

            server.call(new BlockPlaceEvent(server.world.getBlockAt(5, 5, 5), null,
                    server.world.getBlockAt(5, 4, 5), null, player, true, EquipmentSlot.HAND));
            assertEquals(1, counting.asked.get(), "inside the region the same flag is asked");
        }
    }

    @Test
    void aFakePlayerBreakIsItsOwnFlag() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.flag(CoreFlags.DENY_BLOCK_BREAK);   //the parent registers first
            server.box("farm", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK_FAKE_PLAYERS));
            Player machine = server.player("[Quarry]", 20, 5, 20);
            Player human = server.player("farmer", 20, 5, 20);
            server.listen(new RegionBlockListener(EventSource.BREAK, player -> player == machine));

            assertTrue(server.call(breakAt(server, machine, 5, 5, 5)).isCancelled());
            assertFalse(server.call(breakAt(server, human, 5, 5, 5)).isCancelled(), "a real player is not what the flag is about");
        }
    }

    @Test
    void aCancelledBreakIsNotAskedAgain() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            CountingFlag counting = server.flag(new CountingFlag(server.regions.getOwner(), EventSource.BREAK));
            server.box("vault", 0, 0, 0, 10, 10, 10, counting);
            server.listen(new RegionBlockListener(EventSource.BREAK));
            Player player = server.player("miner", 5, 5, 5);

            BlockBreakEvent cancelled = breakAt(server, player, 5, 5, 5);
            cancelled.setCancelled(true);
            server.call(cancelled);
            assertEquals(0, counting.asked.get(), "someone before us already said no");

            server.call(breakAt(server, player, 5, 5, 5));
            assertEquals(1, counting.asked.get(), "an uncancelled break is asked");
        }
    }

    /** A block of {@code type} at x/y/z of the server's world. */
    static Block block(RegionServer server, Material type, int x, int y, int z) {
        World world = server.world;
        return Doubles.of(Block.class)
                .returning("getWorld", world)
                .returning("getType", type)
                .returning("getX", x)
                .returning("getY", y)
                .returning("getZ", z)
                .on("getLocation", args -> new Location(world, x, y, z))
                .build();
    }

    static BlockData data(Material type) {
        return Doubles.of(BlockData.class).returning("getMaterial", type).build();
    }

    static DenySettings settings(String actors, String exceptActors) {
        DenySettings settings = new DenySettings();
        settings.setActors(Collections.singletonList(actors));
        settings.setExceptActors(Collections.singletonList(exceptActors));
        return settings;
    }

    static Enderman enderman(RegionServer server) {
        return RegionServer.entity(Enderman.class, EntityType.ENDERMAN, server.at(5, 5, 5)).build();
    }

    @Test
    void anEndermanTakingABlockIsABreakItsKeyCanExcept() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK));
            server.listen(new RegionBlockListener(EventSource.BREAK));

            assertTrue(server.call(new EntityChangeBlockEvent(enderman(server), server.world.getBlockAt(5, 5, 5), data(Material.AIR))).isCancelled());

            vault.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, settings("", "minecraft:enderman"));
            assertFalse(server.call(new EntityChangeBlockEvent(enderman(server), server.world.getBlockAt(5, 5, 5), data(Material.AIR))).isCancelled(),
                    "the enderman is taken out of what the denial covers");
        }
    }

    @Test
    void denyBlockBreakMobsHoldsTheEndermanAndLetsThePlayerBreak() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.flag(CoreFlags.DENY_BLOCK_BREAK);
            server.box("farm", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK_MOBS));
            server.listen(new RegionBlockListener(EventSource.BREAK));

            assertTrue(server.call(new EntityChangeBlockEvent(enderman(server), server.world.getBlockAt(5, 5, 5), data(Material.AIR))).isCancelled());
            assertFalse(server.call(breakAt(server, server.player("farmer", 5, 5, 5), 5, 5, 5)).isCancelled());
        }
    }

    @Test
    void aFallingBlockLandingIsAPlaceBySomethingThatIsNoMob() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_PLACE));
            server.listen(new RegionBlockListener(EventSource.PLACE));
            FallingBlock sand = RegionServer.entity(FallingBlock.class, EntityType.FALLING_BLOCK, server.at(5, 6, 5)).build();

            assertTrue(server.call(new EntityChangeBlockEvent(sand, block(server, Material.AIR, 5, 5, 5), data(Material.SAND))).isCancelled());

            vault.setFlagSettings(CoreFlags.DENY_BLOCK_PLACE, settings("monster", ""));
            assertFalse(server.call(new EntityChangeBlockEvent(sand, block(server, Material.AIR, 5, 5, 5), data(Material.SAND))).isCancelled(),
                    "falling sand is other, not a monster");
        }
    }

    @Test
    void fireBurningABlockIsTheEnvironmentBreakingIt() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK));
            server.listen(new RegionBlockListener(EventSource.BREAK));

            vault.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, settings("environment", ""));
            assertTrue(server.call(new BlockBurnEvent(server.world.getBlockAt(5, 5, 5), null)).isCancelled());

            vault.setFlagSettings(CoreFlags.DENY_BLOCK_BREAK, settings("player", ""));
            assertFalse(server.call(new BlockBurnEvent(server.world.getBlockAt(5, 5, 5), null)).isCancelled(), "fire is no player");
        }
    }

    @Test
    void fireSetByAPlayerIsTheirsAndFireSpreadingIsNoPlace() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Region vault = server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_PLACE));
            vault.setFlagSettings(CoreFlags.DENY_BLOCK_PLACE, settings("environment", ""));
            server.listen(new RegionBlockListener(EventSource.PLACE));
            Player player = server.player("pyro", 5, 5, 5);

            assertFalse(server.call(new BlockIgniteEvent(server.world.getBlockAt(5, 5, 5), BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player)).isCancelled(),
                    "the player lit it, and players are not what the denial covers");
            assertTrue(server.call(new BlockIgniteEvent(server.world.getBlockAt(5, 5, 5), BlockIgniteEvent.IgniteCause.LAVA, (Entity) null)).isCancelled());
            BlockState fire = Doubles.of(BlockState.class).returning("getType", Material.FIRE).build();
            BlockState grass = Doubles.of(BlockState.class).returning("getType", Material.GRASS_BLOCK).build();
            assertFalse(server.call(new BlockSpreadEvent(server.world.getBlockAt(5, 5, 5), server.world.getBlockAt(4, 5, 5), fire)).isCancelled(),
                    "spreading fire is deny-fire-spread's, never a place");
            assertFalse(server.call(new BlockSpreadEvent(server.world.getBlockAt(5, 5, 5), server.world.getBlockAt(4, 5, 5), grass)).isCancelled(),
                    "grass spreading is nobody placing a block");
        }
    }

    @Test
    void aPistonPushingIntoARegionThatDeniesPlaceIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_PLACE));
            server.listen(new RegionBlockListener(EventSource.PLACE));
            Block outside = block(server, Material.PISTON, 12, 5, 5);
            Block inside = block(server, Material.PISTON, 3, 5, 5);

            //x=12 pushes the block at x=11 west to x=10, inside the region
            assertTrue(server.call(new BlockPistonExtendEvent(outside, Collections.singletonList(server.world.getBlockAt(11, 5, 5)), BlockFace.WEST)).isCancelled());
            //pushing east, away from the region
            assertFalse(server.call(new BlockPistonExtendEvent(outside, Collections.singletonList(server.world.getBlockAt(13, 5, 5)), BlockFace.EAST)).isCancelled());
            assertFalse(server.call(new BlockPistonExtendEvent(inside, Collections.singletonList(server.world.getBlockAt(4, 5, 5)), BlockFace.EAST)).isCancelled(),
                    "a piston moving blocks inside its own region crosses no border");
        }
    }
}
