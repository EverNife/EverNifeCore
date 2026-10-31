package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A screen belongs to the window the SERVER opened, not to the container the renderer created.
 *
 * <p>Handing a container to {@code openInventory} makes the platform build its own window over that storage and hand
 * out a fresh wrapper around it; from then on the open event, every click and the close all name that wrapper. A
 * screen that held on to the object it created would refuse its own open, ignore every click and never hear its own
 * close.</p>
 */
class ChestOpenedContainerTest {

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

    private static Icon diamond() {
        return Icon.of(ChestItems.specOf(new ItemStack(Material.DIAMOND)));
    }

    /** The premise the rest of this class rests on: what comes back is not what went in. */
    @Test
    void theServerAnswersAWindowOfItsOwnOverTheSameStorage() {
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, diamond());
        world.open(screen, player);

        Inventory created = world.getSurface().asInventory();
        Inventory opened = player.getOpenView().getTopInventory();

        assertNotSame(created, opened, "the server names its own wrapper, never the object it was given");
        assertSame(created.getItem(13), opened.getItem(13), "and both read the one storage behind them");
    }

    @Test
    void theScreenIsRegisteredOnTheWindowTheServerOpened() {
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, diamond());
        ScreenView view = world.open(screen, player);

        assertEquals(1, Screens.getOpenCount());
        assertSame(view, Screens.getOpenView(player.getUniqueId()));
        assertTrue(view.isSurface(player.getOpenView().getTopInventory()));
    }

    @Test
    void aClickOnTheWindowTheServerOpenedReachesTheScreen() {
        AtomicInteger runs = new AtomicInteger();
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, diamond()).onClick(context -> runs.incrementAndGet());
        world.open(screen, player);

        InventoryClickEvent event = world.getClicks().leftClick(player, 13);

        assertTrue(event.isCancelled(), "a click the screen never saw would move the icon");
        assertEquals(1, runs.get());
    }

    @Test
    void aCloseOfTheWindowTheServerOpenedTearsTheScreenDown() {
        List<CloseReason> closes = new ArrayList<>();
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, diamond());
        screen.onClose(context -> closes.add(context.getReason()));
        world.open(screen, player);

        player.asPlayer().closeInventory();

        assertEquals(0, Screens.getOpenCount());
        assertEquals(Collections.singletonList(CloseReason.PLAYER_CLOSED), closes);
    }

}
