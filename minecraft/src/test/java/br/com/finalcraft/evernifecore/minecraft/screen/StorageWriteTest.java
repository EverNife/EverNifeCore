package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventoryItemPostUpdateEvent;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.ClickPolicy;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.screen.StorageBinding;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plugin writing an editable area while its screen stays open: a store written behind the window's back is
 * overwritten by the next read-back, so the write goes through the open area ({@link StorageView#of}) and lands in the
 * window and the store together.
 */
class StorageWriteTest {

    private static final int INPUT = 4;
    private static final int BUTTON = 22;

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private StoredInventory store;
    private final List<StoredInventoryItemPostUpdateEvent> told = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        store = new StoredInventory(1);
        store.onPostUpdate(told::add);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    /** One editable slot and a button whose handler is {@code onClick}, handed the open area. */
    private ScreenView open(Consumer<StorageView> onClick) {
        Screen<Layout> screen = Screen.of(3).debounce(0);
        StorageBinding input = screen.storage(Slots.of(INPUT)).backedBy(store).policy(ClickPolicy.EDIT_ALL);
        screen.icon(BUTTON, Icon.of(ChestItems.specOf(new ItemStack(Material.ARROW))))
                .onClick((ClickContext click) -> onClick.accept(StorageView.of(click.getView(), input)));
        return world.open(screen, player);
    }

    private void clickTheButton() {
        world.getClicks().click(player, BUTTON, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        world.advanceTicks(2);
    }

    private static ItemStack diamonds(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }

    @Test
    void aButtonEmptiesTheAreaAndTheScreenStaysOpen() {
        store.setItemSilently(0, diamonds(3));
        List<ItemStack> read = new ArrayList<>();
        open(area -> {
            read.add(area.getItem(0));
            assertTrue(area.setItem(0, null));
        });

        clickTheButton();

        assertEquals(3, read.get(0).getAmount(), "the handler read what the slot held");
        assertNull(world.getSurface().getItem(INPUT), "the player sees the slot empty at once");
        assertNull(store.getItem(0), "and the store holds the same");
        assertEquals(1, Screens.getOpenCount(), "with the screen still open");
        assertNotNull(player.getOpenView());
        assertEquals(1, told.size(), "the store was told once");
        assertEquals(UpdateCause.PLUGIN, told.get(0).getCause(), "as the plugin's change, not a sale to the player");

        player.asPlayer().closeInventory();

        assertNull(store.getItem(0), "and the read-back on close does not bring the diamonds back");
        assertEquals(1, told.size(), "nor reports them a second time");
    }

    @Test
    void aButtonPutsAnItemInTheAreaThatTheCloseReadsBack() {
        open(area -> area.setItem(0, diamonds(5)));

        clickTheButton();
        assertEquals(5, world.getSurface().getItem(INPUT).getAmount());

        player.asPlayer().closeInventory();
        assertEquals(5, store.getItem(0).getAmount());
    }

    @Test
    void whatThePlayerJustPutInIsReadFromTheWindowBeforeTheStoreCatchesUp() {
        List<ItemStack> read = new ArrayList<>();
        open(area -> read.add(area.getItem(0)));
        world.getSurface().placeWithoutRecording(INPUT, diamonds(2));

        world.getClicks().click(player, BUTTON, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        assertNull(store.getItem(0), "the store reads the window back a tick later");
        assertEquals(2, read.get(0).getAmount(), "and the handler already sees what the window shows");
    }

    @Test
    void aStoreThatRefusesKeepsTheSlotAsItWas() {
        store.setItemSilently(0, diamonds(3));
        store.onPreUpdate(event -> event.setCancelled(event.getCause() == UpdateCause.PLUGIN));
        List<Boolean> written = new ArrayList<>();
        open(area -> written.add(area.setItem(0, null)));

        clickTheButton();

        assertFalse(written.get(0));
        assertEquals(3, world.getSurface().getItem(INPUT).getAmount());
        assertEquals(3, store.getItem(0).getAmount());
    }

    @Test
    void aStackBiggerThanTheSlotIsRefusedAndSaysSo() {
        store.setMaxStackSize(0, 4);
        List<RuntimeException> refused = new ArrayList<>();
        open(area -> refused.add(assertThrows(IllegalArgumentException.class, () -> area.setItem(0, diamonds(9)))));

        clickTheButton();

        assertTrue(refused.get(0).getMessage().contains("holds 4"), refused.get(0).getMessage());
        assertNull(world.getSurface().getItem(INPUT));
    }

    @Test
    void aScreenThatClosedHasNoAreaToWrite() {
        Screen<Layout> screen = Screen.of(3);
        StorageBinding input = screen.storage(Slots.of(INPUT)).backedBy(store).policy(ClickPolicy.EDIT_ALL);
        ScreenView view = world.open(screen, player);
        assertNotNull(StorageView.of(view, input));

        player.asPlayer().closeInventory();

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> StorageView.of(view, input));
        assertTrue(refused.getMessage().contains("its store holds what was left"), refused.getMessage());
    }

    @Test
    void anAreaOfAnotherScreenIsNamedAsSuch() {
        ScreenView view = world.open(Screen.of(3), player);
        StorageBinding foreign = Screen.of(3).storage(Slots.of(INPUT)).backedBy(store);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> StorageView.of(view, foreign));
        assertTrue(refused.getMessage().contains("is not an area of"), refused.getMessage());
    }

}
