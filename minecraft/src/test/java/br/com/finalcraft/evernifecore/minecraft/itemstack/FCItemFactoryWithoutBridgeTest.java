package br.com.finalcraft.evernifecore.minecraft.itemstack;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A namespaced id with no NMS bridge behind it - every unit test. */
class FCItemFactoryWithoutBridgeTest {

    @Test
    void aVanillaIdResolvesThroughTheBukkitMaterialOfTheSameName() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            assertFalse(NMSUtils.isSupported(), "a unit test runs with no CraftBukkit behind it");

            ItemStack sword = FCItemFactory.from("minecraft:diamond_sword").build();

            assertEquals(Material.DIAMOND_SWORD, sword.getType());
        }
    }

    @Test
    void aModdedIdIsRefusedWithTheBukkitNameToWriteInstead() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> FCItemFactory.from("mymod:cool_item").build());

            assertTrue(refused.getMessage().contains("NMS bridge") && refused.getMessage().contains("DIAMOND_SWORD"),
                    refused.getMessage());
        }
    }
}
