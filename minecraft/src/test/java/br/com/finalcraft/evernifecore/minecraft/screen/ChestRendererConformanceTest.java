package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.RendererConformance;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The chest renderer keeps every promise the screen SPI asks of a renderer, over the real listener and containers. */
class ChestRendererConformanceTest {

    @TempDir
    Path tempDir;

    @Test
    void theChestPassesTheRendererConformanceSuite() {
        List<String> failures;
        try (ScreenWorld screens = ScreenWorld.open(tempDir.resolve("screens"));
             BukkitScreenWorld chest = BukkitScreenWorld.over(screens, tempDir.resolve("chest"))) {
            failures = RendererConformance.check(chest.getRenderer(), screens);
        }
        assertEquals(Collections.emptyList(), failures);
    }

}
