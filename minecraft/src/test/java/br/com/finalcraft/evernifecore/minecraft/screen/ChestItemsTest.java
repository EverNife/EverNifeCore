package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the chest turns an item id into the stack a slot shows. An id is only checked against the server when it is
 * drawn - a layout file names ids, and only the server a screen opens on knows which of them it has.
 */
class ChestItemsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    @Test
    void aVanillaIdResolvesToItsMaterial() {
        assertEquals(Material.DIAMOND, ChestItems.stackOf(ItemSpec.of("minecraft:diamond", 1)).getType());
        assertEquals(Material.DIAMOND, ChestItems.stackOf(ItemSpec.of("diamond", 1)).getType(),
                "an id without a namespace is a vanilla one");
    }

    @Test
    void aNativeItemIsDrawnAsItself() {
        ItemStack stack = new ItemStack(Material.IRON_INGOT, 5);
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, Icon.of(ChestItems.specOf(stack)));

        world.open(screen, player);

        ItemStack shown = world.getSurface().getItem(13);
        assertTrue(stack.isSimilar(shown), "the stack the plugin handed over, not one rebuilt from its id: " + shown);
        assertEquals(5, shown.getAmount());
    }

    @Test
    void theFirstFallbackThisServerHasWins() {
        String missing = unknownId();
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, Icon.of(ItemSpec.of(missing, 1),
                ItemSpec.of("minecraft:" + UUID.randomUUID().toString().replace("-", ""), 1),
                ItemSpec.of("minecraft:emerald", 1),
                ItemSpec.of("minecraft:gold_ingot", 1)));

        world.open(screen, player);

        assertEquals(Material.EMERALD, world.getSurface().getItem(13).getType());
        assertTrue(warningsNaming(missing).isEmpty(), "an icon a fallback could draw is not worth a warning: "
                + world.getPlatform().getLoggedMessages());
    }

    @Test
    void anIconWhoseMaterialThisServerDoesNotHaveIsDrawnAsAPlaceholderAndWarnedOnce() {
        String missing = unknownId();
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, Icon.of(missing));
        screen.icon(14, Icon.of("minecraft:gold_ingot"));

        world.open(screen, player);
        world.open(screen, world.newPlayer("Alex"));

        Material placeholder = Material.getMaterial("BARRIER") != null ? Material.BARRIER : Material.STONE;
        assertEquals(placeholder, world.getSurface().getItem(13).getType(), "the icon still takes its slot");
        assertEquals(Material.GOLD_INGOT, world.getSurface().getItem(14).getType(),
                "and nothing else on the screen paid for it");
        List<String> warned = warningsNaming(missing);
        assertEquals(1, warned.size(), "the same icon on the same screen is one warning, however often it is "
                + "drawn: " + warned);
        assertTrue(warned.get(0).contains("fallback"), "and it says how to fix it: " + warned.get(0));
    }

    /** An id no server has, fresh per test: a warning is given once per process, not once per test. */
    private static String unknownId() {
        return "minecraft:no_such_item_" + UUID.randomUUID().toString().replace("-", "");
    }

    private List<String> warningsNaming(String id) {
        return world.getPlatform().getLoggedMessages().stream()
                .filter(line -> line.contains(id))
                .collect(Collectors.toList());
    }

}
