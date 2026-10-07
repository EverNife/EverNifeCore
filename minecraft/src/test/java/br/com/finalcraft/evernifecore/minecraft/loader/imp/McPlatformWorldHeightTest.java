package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The height of a world on a 1.21.1 server: the world's own when loaded, the version's otherwise. */
class McPlatformWorldHeightTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void theFloorComesFromTheWorldFrom117() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            world.world("nether", 0, 256);

            assertEquals(WorldHeight.of(0, 255), new McPlatform().getWorldHeight("nether"),
                    "a 1.18+ nether starts at 0, not at the overworld's -64");
        }
    }

    @Test
    void anUnloadedWorldAnswersTheVersionFloor() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            assertEquals(WorldHeight.of(-64, 319), new McPlatform().getWorldHeight("nowhere"));
        }
    }
}
