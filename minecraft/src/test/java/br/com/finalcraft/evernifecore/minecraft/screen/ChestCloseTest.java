package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import br.com.finalcraft.evernifecore.screen.ClickPolicy;
import br.com.finalcraft.evernifecore.screen.ConfirmRequest;
import br.com.finalcraft.evernifecore.screen.ConfirmScreen;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A screen that goes away takes its chest with it. The view is already released when the window is closed - what the
 * cursor held has to go back first - so the window must be found without asking the released view who watched it.
 * Left open, it is a chest nobody guards: its icons can be taken, and an editable area's items, already read back
 * into the store, are taken a second time.
 */
class ChestCloseTest {

    private static final int CONFIRM = 11;
    private static final int DENY = 15;

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        if (world != null) world.close();
    }

    private void leftClick(int slot) {
        world.getClicks().click(player, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        world.advanceTicks(2);
    }

    @Test
    void confirmingARootConfirmationClosesTheChest() {
        world.open(ConfirmScreen.of(ConfirmRequest.of("Sell it?")).debounce(0), player);
        assertNotNull(player.getOpenView());

        leftClick(CONFIRM);

        assertEquals(0, Screens.getOpenCount());
        assertNull(player.getOpenView(), "the answered confirmation left its chest open and unguarded");
    }

    @Test
    void denyingARootConfirmationClosesTheChest() {
        world.open(ConfirmScreen.of(ConfirmRequest.of("Sell it?")).debounce(0), player);

        leftClick(DENY);

        assertEquals(0, Screens.getOpenCount());
        assertNull(player.getOpenView(), "the denied confirmation left its chest open and unguarded");
    }

    @Test
    void closingEveryScreenClosesTheChest() {
        world.open(Screen.of(3), player);
        assertNotNull(player.getOpenView());

        Screens.closeAll();
        world.advanceTicks(2);

        assertEquals(0, Screens.getOpenCount());
        assertNull(player.getOpenView(), "closeAll forgot the screen and left its chest open");
    }

    @Test
    void anEditableAreaClosedByBackIsNotLeftInTheStoreAndTheWindowAtOnce() {
        GenericInventory store = new GenericInventory();
        store.setItem(0, new ItemStack(Material.DIAMOND, 3));
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(10, 11)).backedBy(store).policy(ClickPolicy.EDIT_ALL);
        screen.icon(22, Icon.of(ChestItems.specOf(new ItemStack(Material.ARROW)))).onClick(context -> context.back());
        world.open(screen, player);
        assertEquals(Material.DIAMOND, world.getSurface().getItem(10).getType());

        leftClick(22);

        assertEquals(Material.DIAMOND, store.getItem(0).getType(), "the store read the area back");
        assertNull(player.getOpenView(), "and the window still offered the same diamonds to take");
    }

}
