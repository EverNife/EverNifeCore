package br.com.finalcraft.evernifecore.testkitconsumer;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import br.com.finalcraft.everyconfig.config.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a plugin's Bukkit-side test sees with evernifecore-minecraft and evernifecore-minecraft-tests on
 * its classpath and nothing of this repository's sources: an item built headless, a config holding
 * a location read back with its world - both through the relocated Jackson the test kit brings - and
 * a screen drawn by the real chest renderer, read back slot by slot.
 */
class MinecraftTestKitConsumerTest {

    @TempDir
    Path tempDir;

    @Test
    void anItemIsBuiltWithoutAServer() {
        try (ItemWorld ignored = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            ItemStack sword = FCItemFactory.from(Material.DIAMOND_SWORD).displayName("&bDoom").build();

            assertEquals(Material.DIAMOND_SWORD, sword.getType());
            assertEquals("§bDoom", sword.getItemMeta().getDisplayName());
        }
    }

    @Test
    void aLocationRoundTripsThroughAConfigWithItsWorld() {
        try (ECoreTestWorld platform = Platforms.lenient().install()
                .withPluginExtractor(Plugins.fake("TestKitConsumer", tempDir.toFile()));
             ItemWorld ignored = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            ECPluginData plugin = ECPluginManager.getOrCreateECorePluginData(new Object());
            EverNifeCore.instance.onLoaderInstantiate(plugin);
            ItemWorld.registerConfigTypes();

            Config config = ConfigFactory.open(tempDir.resolve("spawn.yml"));
            config.setValue("Spawn", new Location(ItemWorld.world("lobby"), 1, 64, 2, 90F, 0F));
            config.save();

            Location read = ConfigFactory.open(tempDir.resolve("spawn.yml")).getValue("Spawn", Location.class);
            assertEquals("lobby", read.getWorld().getName());
            assertEquals(64D, read.getY());
        }
    }

    @Test
    void theChestDrawsTheNameAndLoreOfAnIcon() {
        try (BukkitScreenWorld world = BukkitScreenWorld.install(tempDir)) {
            PlayerDouble player = world.newPlayer("Steve");
            Screen<Layout> screen = Screen.of(1);
            screen.icon(0, Icon.of("minecraft:paper").displayName("§eHello").lore("§7World"));
            world.open(screen, player);
            world.advanceTicks(1);

            ItemStack drawn = world.getSurface().getItem(0);
            assertEquals(Material.PAPER, drawn.getType());
            ItemMeta meta = drawn.getItemMeta();
            assertEquals("§eHello", meta.getDisplayName());
            assertEquals(Collections.singletonList("§7World"), meta.getLore());
        }
    }
}
