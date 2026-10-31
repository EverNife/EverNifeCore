package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.ClickSimulator;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.screen.ClickPolicy;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.NamedRegion;
import br.com.finalcraft.evernifecore.screen.Screen;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The part of a click only a chest has: two containers joined in one window, and gestures that reach across them.
 *
 * <p>What a click means once it landed on a slot is neutral and is proven in {@code :common}. What is proven here is
 * the step before: a gesture aimed at the player's own inventory that still reaches into the screen, and a drag the
 * platform spreads over both containers at once.</p>
 */
class ChestClickTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;
    private final AtomicInteger handlerRuns = new AtomicInteger();

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    /** A screen with one button whose handler only records that it ran. */
    private Screen<Layout> withButtonAt(int slot) {
        Screen<Layout> screen = Screen.of(3);
        screen.icon(slot, Icon.of(ChestItems.specOf(new ItemStack(Material.DIAMOND))))
                .onClick(context -> handlerRuns.incrementAndGet());
        return screen;
    }

    @Test
    void theTwoActionsThatReachIntoTheScreenFromOutsideAreJudgedByItsPolicy() {
        world.open(withButtonAt(4), player);

        assertTrue(clicks.clickPlayerInventory(player, 4, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertTrue(clicks.clickPlayerInventory(player, 4, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        assertFalse(clicks.clickPlayerInventory(player, 4, ClickType.LEFT,
                InventoryAction.PICKUP_ALL).isCancelled(), "everything else there stays the player's own");
        assertEquals(0, handlerRuns.get(), "judged, never dispatched");
    }

    @Test
    void aDragThatTouchesTheScreenIsRefusedWholesale() {
        world.open(withButtonAt(13), player);
        ItemStack dragged = new ItemStack(Material.DIRT, 4);

        assertTrue(clicks.drag(player, dragged, 13).isCancelled());
        assertTrue(clicks.drag(player, dragged, 30, 13).isCancelled(),
                "vanilla spreads one drag over both containers, so touching the screen refuses the gesture");
        assertFalse(clicks.drag(player, dragged, 30, 31).isCancelled(),
                "a drag entirely inside the player's own inventory is left alone");
    }

    @Test
    void aRegionThatAllowsDraggingStillCannotBeDraggedIntoWithoutAStore() {
        Screen<Layout> screen = Screen.of(3).addRegion(new NamedRegion("storage", Slots.of(13),
                ClickPolicy.builder().allowDrag().build()));
        world.open(screen, player);

        assertTrue(clicks.drag(player, new ItemStack(Material.DIRT), 13).isCancelled(),
                "the screen still draws that slot, so an item left there is one the next render erases - "
                        + "an area a player may really drag into is declared with storage(...)");
        assertTrue(clicks.drag(player, new ItemStack(Material.DIRT, 2), 12, 13).isCancelled(),
                "one slot of the gesture outside the region refuses all of it");
    }

}
