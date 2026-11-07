package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Bukkit player's whole inventory - main slots, armor and off-hand - held aside by {@link HeldPlayerState} and given back. */
class McHeldInventoryTest {

    private static final int CHESTPLATE = 38;

    @TempDirNobodyCleans
    Path tempDir;

    private final McPlayerStateAccess access = new McPlayerStateAccess();
    private BukkitScreenWorld world;
    private PlayerDouble steve;
    private FPlayer player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        world = BukkitScreenWorld.install(tempDir, platforms -> platforms.playerStateAccess(access));
        steve = world.newPlayer("Steve")
                .wearing(CHESTPLATE, new ItemStack(Material.DIAMOND_CHESTPLATE))
                .withOffHand(new ItemStack(Material.SHIELD));
        player = steve.asFPlayer();
        inventory = steve.asPlayer().getInventory();
        inventory.setItem(0, FCItemFactory.from(Material.DIAMOND_SWORD).displayName("&bDoom").build());
        inventory.setItem(5, new ItemStack(Material.BREAD, 12));
    }

    @AfterEach
    void tearDown() {
        HeldPlayerState.forget(player.getUniqueId(), null);
        if (world != null) world.close();
    }

    @Test
    void anEmptiedInventoryComesBackWholeWithArmorOffHandAndItemNames() {
        String own = access.getInventory(player);

        access.setInventory(player, "");
        for (int slot = 0; slot <= 40; slot++) {
            assertNull(inventory.getItem(slot), "slot " + slot + " is empty");
        }

        access.setInventory(player, own);
        assertEquals(Material.DIAMOND_SWORD, inventory.getItem(0).getType());
        assertEquals("§bDoom", inventory.getItem(0).getItemMeta().getDisplayName());
        assertEquals(12, inventory.getItem(5).getAmount());
        assertEquals(Material.DIAMOND_CHESTPLATE, inventory.getChestplate().getType());
        assertEquals(Material.SHIELD, inventory.getItem(40).getType(), "the off-hand is part of the inventory");
    }

    @Test
    void aDuelKitIsReplacedByTheOwnInventoryWhenTheHolderLetsGo() {
        StateHolder duel = StateHolder.of("theduel:battle");
        HeldPlayerState held = HeldPlayerState.of(player.getUniqueId());
        held.hold(HeldPlayerState.INVENTORY, duel, "", p -> access.setInventory(p, ""), false, player);
        access.setInventory(player, "");
        inventory.setItem(0, new ItemStack(Material.BOW));

        assertTrue(held.release(HeldPlayerState.INVENTORY, duel, player));
        assertEquals(Material.DIAMOND_SWORD, inventory.getItem(0).getType(), "the kit's bow is gone, the own sword is back");
        assertEquals(Material.SHIELD, inventory.getItem(40).getType());
    }
}
