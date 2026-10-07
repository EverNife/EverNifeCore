package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The server's world load and unload reach whoever follows them on the platform. */
class McWorldListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aWorldLoadReachesThePlatformListeners() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            McPlatform platform = new McPlatform();
            List<String> loaded = new ArrayList<>();
            platform.onWorldLoaded(loaded::add);
            world.registerECListener(new McWorldListener(platform));

            world.getPluginManager().callEvent(new WorldLoadEvent(world.world("arena", 0, 256)));

            assertEquals(Collections.singletonList("arena"), loaded);
        }
    }

    @Test
    void aCancelledUnloadReachesNobody() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            McPlatform platform = new McPlatform();
            List<String> unloaded = new ArrayList<>();
            platform.onWorldUnloaded(unloaded::add);
            world.registerECListener(new McWorldListener(platform));
            World arena = world.world("arena", 0, 256);

            WorldUnloadEvent cancelled = new WorldUnloadEvent(arena);
            cancelled.setCancelled(true);
            world.getPluginManager().callEvent(cancelled);
            assertEquals(Collections.emptyList(), unloaded, "the world stayed, so nothing unloaded");

            world.getPluginManager().callEvent(new WorldUnloadEvent(arena));
            assertEquals(Collections.singletonList("arena"), unloaded, "an unload that happens does reach the platform");
        }
    }
}
