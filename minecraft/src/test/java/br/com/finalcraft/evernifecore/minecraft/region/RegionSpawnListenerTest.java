package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.region.flag.core.DenyMonsterSpawnFlag;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A creature the world spawns is asked of the regions; one hatched from an egg is a player acting and never is. */
class RegionSpawnListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private static Zombie zombie(RegionServer server, double x) {
        return RegionServer.entity(Zombie.class, EntityType.ZOMBIE, server.at(x, 5, 5)).build();
    }

    @Test
    void aMonsterSpawnInsideDenyMonsterSpawnIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("town", 0, 0, 0, 10, 10, 10, server.flag(new DenyMonsterSpawnFlag()));
            server.listen(new RegionSpawnListener());
            Cow cow = RegionServer.entity(Cow.class, EntityType.COW, server.at(5, 5, 5)).build();

            assertTrue(server.call(new CreatureSpawnEvent(zombie(server, 5), SpawnReason.NATURAL)).isCancelled());
            assertFalse(server.call(new CreatureSpawnEvent(zombie(server, 50), SpawnReason.NATURAL)).isCancelled(), "outside the town");
            assertFalse(server.call(new CreatureSpawnEvent(cow, SpawnReason.NATURAL)).isCancelled(), "a cow is not a monster");
        }
    }

    @Test
    void anEggSpawnIsNeverAsked() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("town", 0, 0, 0, 10, 10, 10, server.flag(new DenyMonsterSpawnFlag()));
            server.listen(new RegionSpawnListener());

            assertFalse(server.call(new CreatureSpawnEvent(zombie(server, 5), SpawnReason.SPAWNER_EGG)).isCancelled());
            assertTrue(server.call(new CreatureSpawnEvent(zombie(server, 5), SpawnReason.SPAWNER)).isCancelled(),
                    "the same zombie from a spawner block is the world spawning, and is asked");
        }
    }
}
