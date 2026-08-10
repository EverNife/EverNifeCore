package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
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
 * A screen belongs to the window the SERVER opened, not to the container the framework created.
 *
 * <p>Handing a container to {@code openInventory} makes the platform build its own window over that
 * storage and hand out a fresh wrapper around it; from then on the open event, every click and the
 * close all name that wrapper. A screen that held on to the object it created would refuse its own
 * open, ignore every click and never hear its own close - none of them would be about a container it
 * recognises - and the window would sit on screen with nothing behind it.</p>
 */
class GuiOpenedContainerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static Icon diamond() {
        return Icon.of(new ItemStack(Material.DIAMOND));
    }

    /** The premise the rest of this class rests on: what comes back is not what went in. */
    @Test
    void theServerAnswersAWindowOfItsOwnOverTheSameStorage() {
        world.open(Gui.of(3).icon(13, diamond()), player);

        Inventory created = world.getSurface().asInventory();
        Inventory opened = player.getOpenView().getTopInventory();

        assertNotSame(created, opened, "the server names its own wrapper, never the object it was given");
        assertSame(created.getItem(13), opened.getItem(13), "and both read the one storage behind them");
    }

    @Test
    void theScreenIsRegisteredOnTheWindowTheServerOpened() {
        GuiView view = world.open(Gui.of(3).icon(13, diamond()), player);

        assertEquals(1, GuiViews.getOpenCount());
        assertSame(view, GuiViews.getOpenView(player.asPlayer()));
        assertTrue(view.isSurface(player.getOpenView().getTopInventory()));
    }

    @Test
    void aClickOnTheWindowTheServerOpenedReachesTheScreen() {
        AtomicInteger runs = new AtomicInteger();
        world.open(Gui.of(3).icon(13, diamond().onClick(context -> runs.incrementAndGet())), player);

        InventoryClickEvent event = world.getClicks().leftClick(player, 13);

        assertTrue(event.isCancelled(), "a click the screen never saw would move the icon");
        assertEquals(1, runs.get());
    }

    @Test
    void aCloseOfTheWindowTheServerOpenedTearsTheScreenDown() {
        List<CloseReason> closes = new ArrayList<>();
        world.open(Gui.of(3).icon(13, diamond()).onClose(context -> closes.add(context.getReason())), player);

        player.asPlayer().closeInventory();

        assertEquals(0, GuiViews.getOpenCount());
        assertEquals(Collections.singletonList(CloseReason.PLAYER_CLOSED), closes);
    }

}
