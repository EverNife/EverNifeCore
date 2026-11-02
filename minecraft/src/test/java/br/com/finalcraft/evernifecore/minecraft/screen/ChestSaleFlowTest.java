package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.minecraft.itemstack.ComparableItem;
import br.com.finalcraft.evernifecore.minecraft.itemstack.ComparableItemComplex;
import br.com.finalcraft.evernifecore.minecraft.util.FCInventoryUtil;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestEconomy;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import br.com.finalcraft.evernifecore.util.FCEcoUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A shop's whole sale on the real chest: the item counted and taken from the player, the money paid, the thanks sent. */
class ChestSaleFlowTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private TestEconomy economy;

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        economy = Economies.inMemory().balance(player.getUniqueId(), 100).build();
        world.withEconomy(economy);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    public static class SaleLocales {
        @FCLocale(text = "§aSold for 25 coins")
        public static LocaleMessage SOLD;
    }

    @Test
    void aSaleTakesTheItemAndPaysThePlayer() {
        ComparableItem diamond = new ComparableItem(Material.DIAMOND, (short) 0);
        player.getPlayerInventory().set(0, new ItemStack(Material.DIAMOND, 3));
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, Icon.of("minecraft:diamond")).onClick(click -> {
            if (FCInventoryUtil.contains(player.asPlayer(), diamond, 1) && FCInventoryUtil.removeFrom(player.asPlayer(), diamond, 1)) {
                FCEcoUtil.ecoGive(click.getViewer(), 25);
            }
        });
        world.open(screen, player);

        world.getClicks().leftClick(player, 13);
        world.advanceTicks(1);

        assertEquals(0, new BigDecimal("125").compareTo(economy.getBalance(player.getUniqueId())),
                "the sale paid into the in-memory economy: " + economy.getBalance(player.getUniqueId()));
        assertEquals(2, FCInventoryUtil.getAmount(player.asPlayer(), diamond));
    }

    @Test
    void anItemIsCountedAndFoundWithoutAnNmsBridge() {
        player.getPlayerInventory().set(0, new ItemStack(Material.DIAMOND, 3));
        player.getPlayerInventory().set(5, new ItemStack(Material.DIAMOND, 4));
        player.getPlayerInventory().set(6, new ItemStack(Material.EMERALD, 9));

        ComparableItem diamond = new ComparableItem(Material.DIAMOND, (short) 0);
        assertEquals(7, FCInventoryUtil.getAmount(player.asPlayer(), diamond));
        assertTrue(FCInventoryUtil.contains(player.asPlayer(), diamond, 7));
        assertFalse(FCInventoryUtil.contains(player.asPlayer(), diamond, 8));

        ComparableItem complex = new ComparableItemComplex(new ItemStack(Material.DIAMOND));
        assertEquals(7, FCInventoryUtil.getAmount(player.asPlayer(), complex), "a stack with no tag matches a stack with no tag");
        assertTrue(FCInventoryUtil.contains(player.asPlayer(), complex, 7));
    }

    @Test
    void aLocaleMessageReachesThePlayerDouble() {
        FCLocaleManager.loadLocale(world.getPluginData(), true, SaleLocales.class);

        SaleLocales.SOLD.send(player.asFPlayer());

        assertEquals(Collections.singletonList("§aSold for 25 coins"), player.getMessages());
    }

}
