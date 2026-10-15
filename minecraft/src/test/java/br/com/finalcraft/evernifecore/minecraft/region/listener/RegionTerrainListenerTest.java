package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestTerrain;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The journal of a track-changes region hears only what happened: after every plugin decided, never a cancelled event. */
class RegionTerrainListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** Another plugin cancelling the break after the regions allowed it. */
    public static final class LateCanceller implements ECListener {
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onBreak(BlockBreakEvent event) {
            event.setCancelled(true);
        }
    }

    private static Region trackedBox(RegionServer server) {
        server.flag(CoreFlags.TRACK_CHANGES);
        Region region = server.box("arena", 0, 0, 0, 10, 10, 10);
        region.getManager().getTerrain().track(region);
        return region;
    }

    private static TestTerrain terrain(RegionServer server) {
        return server.server.getPlatform().getTestTerrain().clear();
    }

    private static RegionTerrainListener listener() {
        return new RegionTerrainListener((state, content) -> state.getType().name().toLowerCase(Locale.ROOT));
    }

    private static BlockState replaced(Material type) {
        return Doubles.of(BlockState.class).returning("getType", type).build();
    }

    @Test
    void aBreakCancelledByAnotherPluginAfterTheDecisionIsNotRecorded() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            terrain(server);
            Region region = trackedBox(server);
            server.listen(listener());
            server.listen(new LateCanceller());
            Player player = server.player("miner", 5, 5, 5);

            server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player));

            assertEquals(0, region.getManager().getTerrain().getJournalSize(region));
        }
    }

    @Test
    void theSameBreakNotCancelledIsRecorded() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            terrain(server).setBlock(RegionServer.WORLD, 5, 5, 5, "stone");
            Region region = trackedBox(server);
            server.listen(listener());
            Player player = server.player("miner", 5, 5, 5);

            server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player));

            assertEquals(1, region.getManager().getTerrain().getJournalSize(region));
        }
    }

    @Test
    void theFirstChangeOfAPositionWinsOverABreakAndAPlaceAfterIt() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            TestTerrain terrain = terrain(server);
            terrain.setBlock(RegionServer.WORLD, 5, 5, 5, "stone");
            Region region = trackedBox(server);
            server.listen(listener());
            Player player = server.player("builder", 5, 5, 5);

            server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player));
            terrain.setBlock(RegionServer.WORLD, 5, 5, 5, "air");
            server.call(new BlockPlaceEvent(server.world.getBlockAt(5, 5, 5), replaced(Material.AIR), server.world.getBlockAt(5, 4, 5),
                    null, player, true, EquipmentSlot.HAND));
            terrain.setBlock(RegionServer.WORLD, 5, 5, 5, "dirt");
            server.call(new BlockBreakEvent(server.world.getBlockAt(5, 5, 5), player));

            RegionTerrain regionTerrain = region.getManager().getTerrain();
            assertEquals(1, regionTerrain.getJournalSize(region), "three changes of one position keep one entry");
            regionTerrain.restoreJournal(region, 10).join();
            assertEquals("stone", terrain.getBlock(RegionServer.WORLD, 5, 5, 5), "the original before the first change comes back");
        }
    }

    @Test
    void aPlaceRecordsTheStateItReplaced() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            TestTerrain terrain = terrain(server);
            Region region = trackedBox(server);
            server.listen(listener());
            Player player = server.player("builder", 5, 5, 5);

            server.call(new BlockPlaceEvent(server.world.getBlockAt(6, 5, 6), replaced(Material.WATER), server.world.getBlockAt(6, 4, 6),
                    null, player, true, EquipmentSlot.HAND));
            region.getManager().getTerrain().restoreJournal(region, 10).join();

            assertEquals("water", terrain.getBlock(RegionServer.WORLD, 6, 5, 6));
        }
    }
}
