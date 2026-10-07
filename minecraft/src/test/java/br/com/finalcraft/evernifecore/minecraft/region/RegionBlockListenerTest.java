package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyBlockBreakFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyFakePlayerBreakFlag;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A block broken or placed is asked of the regions where it is; a denial cancels the event. */
class RegionBlockListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** Never denies; counts how often it was asked. */
    static final class CountingFlag extends TristateFlag {
        final AtomicInteger asked = new AtomicInteger();

        CountingFlag(ECPluginData owner, EventSource source) {
            super(FlagKey.of(owner, "counting-" + source.name().toLowerCase()), EnumSet.of(source), Tristate.FALSE, Tristate.TRUE);
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
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyBlockBreakFlag(server.regions.getOwner())));
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
            server.box("farm", 0, 0, 0, 10, 10, 10, server.flag(new DenyFakePlayerBreakFlag(server.regions.getOwner())));
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
}
