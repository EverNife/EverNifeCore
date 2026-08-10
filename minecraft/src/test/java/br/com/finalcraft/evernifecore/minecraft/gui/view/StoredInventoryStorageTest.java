package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.ClickSimulator;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventoryItemPostUpdateEvent;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventoryItemPreUpdateEvent;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An editable region over a store that answers back: what a click has to get past before it happens,
 * and what the store is told once it did.
 *
 * <p>The region itself is the same one {@code StorageRegionTest} drives over a plain store. What is
 * different here is the moment the framework asks: a gesture the store may refuse has to be judged
 * while the click can still be cancelled, which means describing what it is about to do instead of
 * reading what it did.</p>
 */
class StoredInventoryStorageTest {

    /** The middle four slots of a 27-slot screen: region index 0 is raw slot 10. */
    private static final int[] AREA = {10, 11, 12, 13};

    @TempDirNobodyCleans
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;
    private StoredInventory store;
    private final List<StoredInventoryItemPreUpdateEvent> asked = new ArrayList<>();
    private final List<StoredInventoryItemPostUpdateEvent> told = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
        store = new StoredInventory(AREA.length);
        store.onPostUpdate(told::add);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private GuiView open() {
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(AREA)).backedBy(store).policy(ClickPolicy.EDIT_ALL);
        return world.openDetachedAndRegistered(gui, player);
    }

    private static ItemStack diamonds(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Being asked before the click happens
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aHandlerThatRefusesTheChangeCancelsTheClickBeforeAnythingMoves() {
        store.setItemSilently(1, diamonds(5));
        store.onPreUpdate(event -> event.setCancelled(true));
        open();

        InventoryClickEvent event = clicks.click(player, 11, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        assertTrue(event.isCancelled(), "refusing has to happen while the click can still be refused - "
                + "reading the slot back afterwards would find the item already gone");
        assertEquals(5, world.getSurface().getItem(11).getAmount());
        world.advanceTicks(1);
        assertEquals(5, store.getItem(1).getAmount());
        assertTrue(told.isEmpty(), "and nothing that did not happen was reported");
    }

    @Test
    void theHandlerIsToldHowManyUnitsTheGestureWouldMove() {
        store.setItemSilently(3, diamonds(6));
        store.onPreUpdate(asked::add);
        open();

        clicks.click(player, 13, ClickType.RIGHT, InventoryAction.PICKUP_HALF);

        assertEquals(1, asked.size());
        assertEquals(3, asked.get(0).getSlot(), "the store's own slot, not the raw slot of the window");
        assertEquals(3, asked.get(0).getRemovedAmount(), "half of six leaves three behind");
        assertEquals(0, asked.get(0).getAddedAmount());
        assertEquals(UpdateCause.PLAYER, asked.get(0).getCause());
    }

    @Test
    void whatArrivesOnASwapIsWhatTheCursorWasHolding() {
        store.setItemSilently(0, diamonds(2));
        store.onPreUpdate(asked::add);
        open();
        player.holding(new ItemStack(Material.DIRT, 3));

        clicks.click(player, 10, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);

        assertEquals(2, asked.get(0).getRemovedAmount(), "the diamonds left");
        assertEquals(3, asked.get(0).getAddedAmount(), "and the dirt arrived");
        assertEquals(Material.DIRT, asked.get(0).getNewItem().getType());
    }

    @Test
    void aGestureTheFrameworkCannotDescribeIsRefusedByAStoreThatVets() {
        store.setItemSilently(0, diamonds(2));
        open();

        assertFalse(clicks.click(player, 10, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_MOVE_AND_READD)
                .isCancelled(), "a store with nothing to say lets the platform get on with it");

        store.onPreUpdate(asked::add);
        assertTrue(clicks.click(player, 10, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_MOVE_AND_READD)
                .isCancelled(), "a store that judges its updates cannot be handed a gesture nobody can "
                + "describe: letting it through would be the way around the handler");
        assertTrue(asked.isEmpty(), "and there was nothing to ask about");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Being told after it happened
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void onlyTheSlotThatChangedIsReportedAndOnlyOnce() {
        store.setItemSilently(0, diamonds(4));
        store.setItemSilently(1, new ItemStack(Material.DIRT));
        open();

        clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        //the platform is what moves the item; the framework's half is reading the result back
        world.getSurface().placeWithoutRecording(10, null);
        world.advanceTicks(1);

        assertEquals(1, told.size());
        assertEquals(0, told.get(0).getSlot());
        assertEquals(4, told.get(0).getRemovedAmount());
        assertNull(told.get(0).getNewItem());

        world.advanceTicks(5);
        assertEquals(1, told.size(), "a tick in which nothing changed reports nothing");
    }

    @Test
    void aHandlerToldOfAChangeFindsTheStoreAlreadyHoldingIt() {
        List<ItemStack> asStored = new ArrayList<>();
        store.onPostUpdate(event -> asStored.add(event.getInventory().getItem(event.getSlot())));
        open();

        clicks.click(player, 12, ClickType.LEFT, InventoryAction.PLACE_ALL);
        world.getSurface().placeWithoutRecording(12, diamonds(3));
        world.advanceTicks(1);

        assertEquals(1, asStored.size());
        assertEquals(3, asStored.get(0).getAmount(), "which is what a handler that persists writes out");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  How much one slot holds
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aShiftClickFillsASlotToItsOwnMaximumAndLeavesTheRest() {
        store.setMaxStackSize(0, 3);
        store.setMaxStackSize(1, 2);
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(10, 11)).backedBy(store).policy(ClickPolicy.EDIT_ALL);
        world.openDetachedAndRegistered(gui, player);
        SurfaceDouble surface = world.getSurface();
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(9));

        clicks.clickPlayerInventory(player, 9, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertEquals(3, surface.getItem(10).getAmount(), "the first slot holds three of anything");
        assertEquals(2, surface.getItem(11).getAmount(), "the second holds two");
        assertEquals(4, player.getPlayerInventory().getItem(9).getAmount(),
                "and exactly what went in came off the stack it came from");
    }

    @Test
    void aGestureThatWouldOverfillASlotIsRefusedWhole() {
        store.setMaxStackSize(0, 4);
        open();
        player.holding(diamonds(10));

        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(),
                "ten does not fit a slot that holds four, and the framework does not rewrite a gesture "
                        + "the platform would carry out whole");
        assertFalse(clicks.click(player, 11, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(),
                "the slot next to it holds what the item allows");
    }

    @Test
    void takingFromAnOverfilledSlotIsStillAllowed() {
        //a slot filled before anybody said how much it holds - refusing to empty it would strand it
        store.setItemSilently(0, diamonds(20));
        store.setMaxStackSize(0, 4);
        open();

        assertFalse(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertFalse(clicks.click(player, 10, ClickType.RIGHT, InventoryAction.PICKUP_HALF).isCancelled(),
                "what is already there is never cut down to size, and never locked in either");
    }

    @Test
    void anOverfilledSlotDoesNotLetSomethingElseInAtTheSameSize() {
        store.setItemSilently(0, diamonds(20));
        store.setMaxStackSize(0, 4);
        open();
        player.holding(new ItemStack(Material.DIRT, 20));

        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled(),
                "what a slot over its maximum may still do is be emptied, not be refilled with something "
                        + "else the same size - a different item is a new stack, and a new stack has to fit");
        assertEquals(Material.DIAMOND, world.getSurface().getItem(10).getType());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The drag, which a store that vets never leaves to the platform
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aDragOverAStoreThatVetsIsDividedHereEvenWhenEverySlotWouldTakeIt() {
        store.onPreUpdate(asked::add);
        open();
        ItemStack dragged = diamonds(2);
        player.holding(dragged);

        InventoryDragEvent event = clicks.dragEvenly(player, dragged, 10, 11);

        assertTrue(event.isCancelled(), "the platform divides a drag without saying how much lands where, "
                + "and that number is the whole of what the handler is asked about");
        assertEquals(1, world.getSurface().getItem(10).getAmount());
        assertEquals(1, world.getSurface().getItem(11).getAmount());
        assertEquals(2, asked.size(), "one question per slot, each with its own share");
        assertEquals(1, asked.get(0).getAddedAmount());
        assertEquals(1, asked.get(1).getAddedAmount(), "the second share is a question of its own, and "
                + "a handler reading only the first would never see it");
        assertTrue(GuiBuffer.isEmpty(player.getCursor()), "and all of it left the cursor");
    }

    @Test
    void aSlotThatRefusesADragShareTakesNothingAndTheRestGoesOn() {
        store.onPreUpdate(event -> event.setCancelled(event.getSlot() == 1));
        open();
        ItemStack dragged = diamonds(2);
        player.holding(dragged);

        clicks.dragEvenly(player, dragged, 10, 11);

        assertEquals(1, world.getSurface().getItem(10).getAmount());
        assertNull(world.getSurface().getItem(11), "the slot that refused got nothing");
        assertEquals(1, player.getCursor().getAmount(), "and its share stayed on the cursor");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declarations that cannot work
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aRegionWiderThanItsStoreGrowsTheStoreItCanGrow() {
        StoredInventory smaller = new StoredInventory(3);
        smaller.setItemSilently(2, diamonds(4));
        Gui<LayoutBase> gui = Gui.of(3);
        gui.storage(Slots.of(10, 11, 12, 13, 14)).backedBy(smaller).policy(ClickPolicy.EDIT_ALL);

        world.openDetachedAndRegistered(gui, player);

        assertEquals(5, smaller.getCapacity(), "the region is what declares how big the area is");
        assertEquals(4, world.getSurface().getItem(12).getAmount(), "and growing one loses nothing");
    }

    @Test
    void aStoreThatCannotGrowStillRefusesToOpenAndSaysWhereToGrowIt() {
        Gui<LayoutBase> gui = Gui.of(3);
        gui.storage(Slots.of(10, 11, 12, 13, 14)).backedBy(new BoundedStore(3));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> world.openDetachedAndRegistered(gui, player));

        assertTrue(failure.getMessage().contains("grow the store to 5 slots"), failure.getMessage());
        assertTrue(failure.getMessage().contains("main thread"), "growing one is a main-thread change, and "
                + "the message is where the caller finds that out: " + failure.getMessage());
    }

    @Test
    void aStoreThatCameBackSmallerThanTheRegionShowsEverythingItHeld() throws IOException {
        Path file = tempDir.resolve("backpack.yml");
        Files.write(file, versionOneFileFilledUpToSlotTen().getBytes(StandardCharsets.UTF_8));
        StoredInventory restored = ConfigFactory.open(file).getValue("backpack", StoredInventory.class);
        assertEquals(11, restored.getCapacity(), "the shape before the envelope ends where its items do");

        int[] wholeScreen = new int[27];
        for (int slot = 0; slot < wholeScreen.length; slot++) {
            wholeScreen[slot] = slot;
        }
        Gui<LayoutBase> gui = Gui.of(3);
        gui.storage(Slots.of(wholeScreen)).backedBy(restored).policy(ClickPolicy.EDIT_ALL);

        world.openDetachedAndRegistered(gui, player);

        assertEquals(27, restored.getCapacity(), "a backpack whose owner only ever filled ten slots is "
                + "still a backpack of twenty-seven, and the region is what remembers that");
        for (int slot = 0; slot <= 10; slot++) {
            assertNotNull(world.getSurface().getItem(slot), "slot " + slot + " was in the file");
            assertEquals(slot + 1, world.getSurface().getItem(slot).getAmount(), "slot " + slot);
        }
    }

    /** The shape every inventory was stored in before the envelope: a bare slot map, filled to slot 10. */
    private static String versionOneFileFilledUpToSlotTen() {
        StringBuilder file = new StringBuilder("backpack:\n");
        for (int slot = 0; slot <= 10; slot++) {
            file.append("  '").append(slot).append("':\n")
                    .append("  - 'type: DIAMOND'\n")
                    .append("  - 'amount: ").append(slot + 1).append("'\n");
        }
        return file.toString();
    }

    /** A store with a capacity it cannot change - every store that is not a {@link StoredInventory}. */
    private static final class BoundedStore implements ItemStore {

        private final ItemStack[] items;

        private BoundedStore(int capacity) {
            this.items = new ItemStack[capacity];
        }

        @Override
        public int getCapacity() {
            return items.length;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.length ? items[slot] : null;
        }

        @Override
        public void setItemSilently(int slot, ItemStack item) {
            if (slot >= 0 && slot < items.length) {
                items[slot] = item;
            }
        }

        @Override
        public int[] getOccupiedSlots() {
            return new int[0];
        }

    }

    @Test
    void aStoreBiggerThanItsRegionKeepsWhatIsOutOfReach() {
        StoredInventory bigger = new StoredInventory(6);
        bigger.setItemSilently(5, diamonds(2));
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(10, 11)).backedBy(bigger).policy(ClickPolicy.EDIT_ALL);
        GuiView view = world.openDetachedAndRegistered(gui, player);

        world.closeDetached(view);

        assertEquals(2, bigger.getItem(5).getAmount(), "an item a region cannot see is left alone, "
                + "never erased by the read that follows a change");
    }

}
