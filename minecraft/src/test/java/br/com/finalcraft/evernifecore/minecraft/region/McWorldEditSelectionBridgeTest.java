package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.minecraft.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A server without WorldEdit: the adapter loads, says it is absent, and nothing that asks for a selection throws. */
class McWorldEditSelectionBridgeTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void withoutWorldEditIsPresentIsFalseAndNothingThrows() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            assertFalse(WEPlatform.isPresent());

            IllegalStateException refused = assertThrows(IllegalStateException.class, WEPlatform::getInstance);
            assertTrue(refused.getMessage().contains("isPresent()"), "the message names the check to make first: " + refused.getMessage());
        }
    }

    @Test
    void withoutWorldEditThereIsNoSelection() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            Player player = world.player("builder", new Location(world.world("world", -64, 320), 0, 64, 0));

            assertNull(McWorldEditSelectionBridge.selectionOf(player));
        }
    }
}
