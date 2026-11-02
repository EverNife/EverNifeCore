package br.com.finalcraft.evernifecore.minecraft.delivery;

import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** From 1.9 on the inventory's contents carry armor and the off-hand after the main slots; a trade reads only the main ones. */
class McInventoryDestinationSlotsTest {

    private static final int BACKPACK = 9;
    private static final int CHESTPLATE = 38;

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private final McInventoryDestination inventory = new McInventoryDestination();

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.installWithItemMetadata(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    @Test
    void wornArmorIsNeitherCountedNorTaken() {
        PlayerDouble player = world.newPlayer("Steve").wearing(CHESTPLATE, new ItemStack(Material.DIAMOND_CHESTPLATE));
        player.getPlayerInventory().set(BACKPACK, new ItemStack(Material.DIAMOND_CHESTPLATE));
        ItemMatcher chestplate = ItemMatcher.of("minecraft:diamond_chestplate");

        assertEquals(1, inventory.count(player.asFPlayer(), chestplate));
        assertTrue(inventory.take(player.asFPlayer(), chestplate, 1));
        assertNull(player.getPlayerInventory().getItem(BACKPACK));
        assertNotNull(player.asPlayer().getInventory().getItem(CHESTPLATE), "the worn chestplate stays on");
    }

    @Test
    void theOffHandIsNeitherCountedNorTaken() {
        PlayerDouble player = world.newPlayer("Steve").withOffHand(new ItemStack(Material.DIAMOND, 5));
        player.getPlayerInventory().set(BACKPACK, new ItemStack(Material.DIAMOND, 3));
        ItemMatcher diamond = ItemMatcher.of("minecraft:diamond");

        assertEquals(3, inventory.count(player.asFPlayer(), diamond));
        assertTrue(inventory.take(player.asFPlayer(), diamond, 3));
        assertNull(player.getPlayerInventory().getItem(BACKPACK));
        assertEquals(5, player.asPlayer().getInventory().getItem(40).getAmount(), "the off-hand keeps all it held");
    }

    @Test
    void takeOfMoreThanTheMainInventoryHoldsTakesNothing() {
        PlayerDouble player = world.newPlayer("Steve").wearing(CHESTPLATE, new ItemStack(Material.DIAMOND_CHESTPLATE));
        player.getPlayerInventory().set(BACKPACK, new ItemStack(Material.DIAMOND_CHESTPLATE));
        ItemMatcher chestplate = ItemMatcher.of("minecraft:diamond_chestplate");

        assertFalse(inventory.take(player.asFPlayer(), chestplate, 2));
        assertNotNull(player.getPlayerInventory().getItem(BACKPACK), "a refused take removes nothing");
        assertNotNull(player.asPlayer().getInventory().getItem(CHESTPLATE));
    }
}
