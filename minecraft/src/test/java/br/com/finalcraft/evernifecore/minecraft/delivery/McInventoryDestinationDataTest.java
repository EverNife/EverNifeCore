package br.com.finalcraft.evernifecore.minecraft.delivery;

import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The inventory describes a stack with its data value, so a matcher of one colour or damage tells it apart. */
class McInventoryDestinationDataTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.installWithItemMetadata(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    @Test
    @SuppressWarnings("deprecation")
    void aStackIsDescribedWithItsDamageAsTheDataValue() {
        ItemSpec described = McInventoryDestination.describe(new ItemStack(Material.DIAMOND_SWORD, 1, (short) 14));

        assertEquals(14, described.getData());
        assertTrue(ItemMatcher.of("minecraft:diamond_sword", 14).matches(described));
        assertFalse(ItemMatcher.of("minecraft:diamond_sword", 0).matches(described), "a fresh sword is data 0");
        assertTrue(ItemMatcher.of("minecraft:diamond_sword").matches(described), "a matcher without data takes any damage");
    }

    @Test
    void aStackWithoutDamageIsDataZeroNotUnspecified() {
        assertEquals(0, McInventoryDestination.describe(new ItemStack(Material.DIAMOND_SWORD)).getData());
    }
}
