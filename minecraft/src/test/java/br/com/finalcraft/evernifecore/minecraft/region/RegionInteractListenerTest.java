package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractBlocksFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractEntitiesFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractFlag;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A block or an entity a player uses is asked of the regions; the listener builds the context, the core picks the flag. */
class RegionInteractListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private static final class Interact {
        final DenyInteractFlag any;
        final DenyInteractBlocksFlag blocks;
        final DenyInteractEntitiesFlag entities;

        Interact(RegionServer server) {
            any = server.flag(new DenyInteractFlag());
            blocks = server.flag(new DenyInteractBlocksFlag(any));
            entities = server.flag(new DenyInteractEntitiesFlag(any));
        }
    }

    private static PlayerInteractEvent clickBlock(RegionServer server, Player player, int x, int y, int z) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, server.world.getBlockAt(x, y, z), BlockFace.UP);
    }

    private static PlayerInteractEntityEvent clickVillager(RegionServer server, Player player, double x, double y, double z) {
        Villager villager = RegionServer.entity(Villager.class, EntityType.VILLAGER, server.at(x, y, z)).build();
        return new PlayerInteractEntityEvent(player, villager);
    }

    @Test
    void aBlockClickInsideDenyInteractIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("shop", 0, 0, 0, 10, 10, 10, new Interact(server).any);
            server.listen(new RegionInteractListener());
            Player player = server.player("visitor", 5, 5, 5);

            assertTrue(server.call(clickBlock(server, player, 5, 5, 5)).isCancelled());
            assertFalse(server.call(clickBlock(server, player, 20, 5, 20)).isCancelled(), "outside the region the click goes through");
        }
    }

    @Test
    void anAirClickIsNeverAsked() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            RegionBlockListenerTest.CountingFlag counting = server.flag(new RegionBlockListenerTest.CountingFlag(server.regions.getOwner(), EventSource.INTERACT));
            server.box("shop", 0, 0, 0, 10, 10, 10, counting);
            server.listen(new RegionInteractListener());
            Player player = server.player("visitor", 5, 5, 5);

            server.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.SELF));
            assertEquals(0, counting.asked.get(), "Bukkit reports an air click cancelled, so it never reaches the regions");

            server.call(clickBlock(server, player, 5, 5, 5));
            assertEquals(1, counting.asked.get(), "a block click where the player stands is asked");
        }
    }

    @Test
    void anEntityClickCarriesTheEntityTypeAsId() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Interact interact = new Interact(server);
            server.box("stable", 0, 0, 0, 10, 10, 10, interact.entities);
            server.box("garden", 100, 0, 100, 110, 10, 110, interact.blocks);
            server.listen(new RegionInteractListener());
            Player player = server.player("rider", 5, 5, 5);

            assertTrue(server.call(clickVillager(server, player, 5, 5, 5)).isCancelled(), "deny-interact-entities speaks for an entity");
            assertFalse(server.call(clickBlock(server, player, 5, 5, 5)).isCancelled(), "and says nothing about a block of the same region");
            assertFalse(server.call(clickVillager(server, player, 105, 5, 105)).isCancelled(),
                    "deny-interact-blocks says nothing about an entity: the listener built an EntityContext");
        }
    }
}
