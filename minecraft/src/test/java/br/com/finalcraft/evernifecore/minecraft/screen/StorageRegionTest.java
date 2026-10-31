package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventoryStore;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.ClickSimulator;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.ClickKind;
import br.com.finalcraft.evernifecore.screen.ClickPolicy;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.screen.StorageChange;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one area of a screen where a click is not cancelled, which makes it the one place a bug is a
 * duplicated or a destroyed item.
 *
 * <p>Every test here is about that arithmetic. The platform is what actually moves an item, and no test
 * has one, so what is driven is the framework's two halves: whether the gesture is allowed through, and
 * what the framework does with the slots afterwards - which of them it writes, which it refuses to
 * touch, and how much it takes off the source of a gesture it had to carry out itself.</p>
 */
class StorageRegionTest {

    /** The middle of a 27-slot screen: region index 0 is raw slot 10, index 3 is raw slot 13. */
    private static final int[] AREA = {10, 11, 12, 13};

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;
    private GenericInventory store;
    private final List<StorageChange> changes = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
        store = new GenericInventory();
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    /** A screen whose middle four slots are editable under {@code policy}. */
    private Screen<Layout> screen(ClickPolicy policy) {
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(AREA)).backedBy(store).policy(policy).onChange(changes::add);
        return screen;
    }

    private ScreenView open(Screen<?> screen) {
        return world.open(screen, player);
    }

    private void closeByPlayer() {
        player.asPlayer().closeInventory();
    }

    private static ItemStack diamonds(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }

    private static Icon iconOf(Material material) {
        return Icon.of(ChestItems.specOf(new ItemStack(material)));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Opening: the store reaches the window, and nothing of the screen reaches those slots
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theRegionOpensShowingWhatTheStoreHolds() {
        store.setItem(0, diamonds(3));
        store.setItem(3, new ItemStack(Material.DIRT));
        open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.DIAMOND, surface.getItem(10).getType());
        assertEquals(3, surface.getItem(10).getAmount());
        assertEquals(Material.DIRT, surface.getItem(13).getType(), "index 3 of the store is the region's 4th slot");
        assertNull(surface.getItem(11), "an index the store never filled stays empty");
        assertNotSame(store.getItem(0), surface.getItem(10), "the store's own stack never reaches the window");
    }

    @Test
    void nothingIsPaintedOverAnEditableSlot() {
        store.setItem(0, diamonds(1));
        Screen<Layout> screen = screen(ClickPolicy.EDIT_ALL);
        screen.icon(Slots.of(10, 11), iconOf(Material.STONE).background());
        ScreenView view = open(screen);
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.DIAMOND, surface.getItem(10).getType(), "the region's item is what shows");
        assertNull(surface.getItem(11), "and an empty region slot stays empty instead of showing the icon");
        assertNull(view.getIconAt(10), "a slot the player owns carries no icon to dispatch");
    }

    @Test
    void aResyncLeavesTheRegionAlone() {
        ScreenView view = open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble surface = world.getSurface();
        surface.placeWithoutRecording(11, diamonds(1));

        view.resync();

        assertEquals(Material.DIAMOND, surface.getItem(11).getType(), "putting the screen back cannot blank a "
                + "slot the framework stopped drawing - that would be the player's item destroyed");
    }

    @Test
    void aReplacedContainerKeepsWhatTheRegionHeld() {
        ScreenView view = open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble first = world.getSurface();
        first.placeWithoutRecording(11, diamonds(3));

        view.getSurface().setTitle("Renamed");

        assertNotSame(first, world.getSurface(), "a title change costs a new container");
        assertEquals(3, ChestScreenRenderer.surfaceOf(view).getItem(11).getAmount(), "and the framework does not "
                + "redraw these slots - it has to carry them across");
    }

    @Test
    void theStoreIsOnlyReadAsFarAsTheRegionReaches() {
        store.setItem(0, diamonds(1));
        store.setItem(9, new ItemStack(Material.DIRT));
        open(screen(ClickPolicy.EDIT_ALL));

        assertEquals(1, world.getSurface().getWrittenSlots().size(), "only the indexes the region reaches "
                + "were drawn: " + world.getSurface().getWrites());
        assertTrue(logged("past that"), "and the plugin is told the rest is out of reach: " + loggedMessages());

        closeByPlayer();
        assertEquals(Material.DIRT, store.getItem(9).getType(), "an item out of reach is left untouched, "
                + "never erased by a region that cannot see it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Which gestures go through
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aTakeThePolicyAllowsGoesThroughAndTheStoreFollows() {
        store.setItem(1, diamonds(5));
        open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble surface = world.getSurface();

        InventoryClickEvent event = clicks.click(player, 11, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        assertFalse(event.isCancelled());
        //the platform is what moves the item; the framework's half is reading the result back
        surface.placeWithoutRecording(11, null);
        world.advanceTicks(1);

        assertNull(store.getItem(1), "what the container no longer holds the store no longer holds");
        assertEquals(1, changes.size());
        assertFalse(changes.get(0).isLast());
    }

    @Test
    void aTakeFromAnAreaThatOnlyAcceptsIsRefused() {
        store.setItem(0, diamonds(1));
        open(screen(ClickPolicy.builder().allowPlace().build()));

        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        world.advanceTicks(1);
        assertTrue(changes.isEmpty(), "a refused click changed nothing, so there is nothing to report");
    }

    @Test
    void aShiftMoveIsJudgedAsWhicheverDirectionItRuns() {
        store.setItem(0, diamonds(1));
        open(screen(ClickPolicy.builder().allowPlace().build()));

        assertTrue(clicks.click(player, 10, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                .isCancelled(), "leaving the region is a take, and taking was never allowed");
    }

    @Test
    void aButtonOnAnEditableScreenIsStillCancelledAndStillRuns() {
        AtomicInteger runs = new AtomicInteger();
        Screen<Layout> screen = screen(ClickPolicy.EDIT_ALL);
        screen.icon(0, iconOf(Material.STONE)).onClick(context -> runs.incrementAndGet());
        open(screen);

        assertTrue(clicks.leftClick(player, 0).isCancelled(), "the region opened up its own slots, not the screen");
        assertEquals(1, runs.get());
        assertFalse(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
    }

    @Test
    void aDoubleClickIsRefusedAnywhereOnAnEditableScreen() {
        assertFalse(ClickPolicy.EDIT_ALL.allowsKind("DOUBLE_CLICK", ClickKind.COLLECT_TO_CURSOR),
                "gathering is not part of editing");
        //a policy that does allow it, to prove the refusal is the screen's and not the policy's
        open(screen(ClickPolicy.builder().allowEverything().build()));

        assertTrue(clicks.click(player, 10, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR)
                .isCancelled(), "it would gather from every slot of the window at once");
        assertTrue(clicks.click(player, 0, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR)
                .isCancelled(), "including the screen's own icons");
        assertTrue(clicks.clickPlayerInventory(player, 4, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR)
                .isCancelled(), "and aiming it from below changes nothing");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Which items go in
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anItemTheFilterRefusesCannotGoInEvenWhenPlacingIsAllowed() {
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .denyPlace(ChestItems.stacks(item -> item.getType() == Material.DIRT))
                .onChange(changes::add);
        open(screen);

        player.holding(new ItemStack(Material.DIRT));
        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());

        player.holding(diamonds(1));
        assertFalse(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(),
                "the filter is about the item, not about the gesture");
    }

    @Test
    void anAllowPlaceFilterLetsNothingElseIn() {
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .allowPlace(ChestItems.stacks(item -> item.getType() == Material.DIAMOND));
        open(screen);

        player.holding(new ItemStack(Material.DIRT));
        assertTrue(clicks.click(player, 10, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());

        player.holding(diamonds(1));
        assertFalse(clicks.click(player, 10, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());
    }

    @Test
    void aSwapIsJudgedByWhatTheCursorWouldLeaveBehind() {
        store.setItem(0, diamonds(1));
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .denyPlace(ChestItems.stacks(item -> item.getType() == Material.DIRT));
        open(screen);

        player.holding(new ItemStack(Material.DIRT));
        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled(),
                "a swap puts an item in as well as taking one out");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The gestures the framework has to carry out itself
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aShiftClickFromBelowPoursIntoTheRegionAndTakesItOffTheSource() {
        open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble surface = world.getSurface();
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(5));

        InventoryClickEvent event = clicks.clickPlayerInventory(player, 9, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertTrue(event.isCancelled(), "the platform would have spread it over the screen's buttons too");
        assertEquals(5, surface.getItem(10).getAmount(), "the whole stack fitted in the region's first slot");
        assertNull(player.getPlayerInventory().getItem(9), "and exactly that much left the slot it came from");

        world.advanceTicks(1);
        assertEquals(5, store.getItem(0).getAmount());
    }

    @Test
    void aShiftClickIntoAReadOnlyRegionMovesNothing() {
        open(screen(ClickPolicy.builder().allowTake().build()));
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(5));

        InventoryClickEvent event = clicks.clickPlayerInventory(player, 9, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertTrue(event.isCancelled());
        assertNull(world.getSurface().getItem(10));
        assertEquals(5, player.getPlayerInventory().getItem(9).getAmount(), "nothing left the source either");
    }

    @Test
    void aRegionWithNoRoomTakesNothingMore() {
        store.setItem(0, new ItemStack(Material.DIRT));
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(10)).backedBy(store).policy(ClickPolicy.EDIT_ALL).onChange(changes::add);
        open(screen);
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(5));

        clicks.clickPlayerInventory(player, 9, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertEquals(Material.DIRT, world.getSurface().getItem(10).getType(), "the one slot was taken");
        assertEquals(5, player.getPlayerInventory().getItem(9).getAmount(),
                "so nothing was taken off the source - a stack that fits nowhere has not moved");
    }

    @Test
    void aDragIsDividedSlotBySlot() {
        open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble surface = world.getSurface();
        ItemStack dragged = diamonds(2);
        player.holding(dragged);

        //slot 9 is outside the region, so it is a slot the screen still draws
        InventoryDragEvent event = clicks.dragEvenly(player, dragged, 9, 10);

        assertTrue(event.isCancelled(), "one refused slot means the platform cannot have the gesture");
        assertNull(surface.getItem(9), "and the slot it refused received nothing");
        assertEquals(1, surface.getItem(10).getAmount(), "the region's slot got its own share");
        assertEquals(1, player.getCursor().getAmount(), "and exactly that much left the cursor");

        world.advanceTicks(1);
        assertEquals(1, store.getItem(0).getAmount());
    }

    @Test
    void aDragEveryRegionSlotAcceptsIsLeftToThePlatform() {
        open(screen(ClickPolicy.EDIT_ALL));
        ItemStack dragged = diamonds(2);
        player.holding(dragged);

        InventoryDragEvent event = clicks.dragEvenly(player, dragged, 10, 11);

        assertFalse(event.isCancelled(), "nothing to take apart: every slot it touches accepts it");
        assertNull(world.getSurface().getItem(10), "so the framework wrote nothing - the platform would have");

        //it still reads itself back afterwards, which is what the platform's write needs
        world.getSurface().placeWithoutRecording(10, diamonds(1));
        world.advanceTicks(1);
        assertEquals(1, changes.size());
        assertEquals(1, store.getItem(0).getAmount());
    }

    @Test
    void aDragOverAnIconAndAnEditableSlotIsRefusedWholeAndLosesNothing() {
        //the screen itself is open too, which is the only arrangement in which a drag over an icon is
        //not already refused by the policy
        Screen<Layout> screen = screen(ClickPolicy.EDIT_ALL).policy(ClickPolicy.EDIT_ALL);
        //an icon of the very item being dragged: a client only ever aims a drag at a slot it can merge with
        screen.icon(9, Icon.of(ChestItems.specOf(diamonds(1))));
        ScreenView view = open(screen);
        SurfaceDouble surface = world.getSurface();
        player.holding(diamonds(2));
        int startedWith = diamondsWithinReach();

        InventoryDragEvent event = clicks.dragEvenly(player, player.getCursor(), 9, 10);

        assertTrue(event.isCancelled(), "the screen draws slot 9, so the platform cannot have the gesture");
        assertEquals(1, surface.getItem(9).getAmount(),
                "the icon is left as it was - an item written over it is one the next render erases");
        assertEquals(1, surface.getItem(10).getAmount(), "only the editable slot took a share");
        assertEquals(1, player.getCursor().getAmount(), "and exactly that much left the cursor");
        assertEquals(startedWith, diamondsWithinReach());

        view.resync();
        world.advanceTicks(1);

        assertEquals(startedWith, diamondsWithinReach(), "and the render that follows loses none of it");
        assertEquals(1, surface.getItem(9).getAmount());
    }

    @Test
    void aDragOfAnItemTheFilterRefusesIsRefusedWhole() {
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .denyPlace(ChestItems.stacks(item -> item.getType() == Material.DIRT));
        open(screen);
        ItemStack dragged = new ItemStack(Material.DIRT, 2);
        player.holding(dragged);

        InventoryDragEvent event = clicks.dragEvenly(player, dragged, 10, 11);

        assertTrue(event.isCancelled());
        assertNull(world.getSurface().getItem(10));
        assertEquals(2, player.getCursor().getAmount(), "the cursor kept all of it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The arithmetic, step by step rather than at the end
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyStepOfASequenceLeavesTheSameNumberOfItemsInReach() {
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(8));
        player.holding(diamonds(4));
        open(screen(ClickPolicy.EDIT_ALL));
        int startedWith = diamondsWithinReach();
        assertEquals(12, startedWith, "eight in the player's own inventory and four on the cursor");

        clicks.clickPlayerInventory(player, 9, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertEquals(startedWith, diamondsWithinReach(), "after the stack was poured into the region");

        //slot 9 is outside the region, so the gesture is one the framework has to carry out itself
        clicks.dragEvenly(player, player.getCursor(), 9, 11);
        assertEquals(startedWith, diamondsWithinReach(), "after a drag the region took only half of");

        clicks.dragEvenly(player, player.getCursor(), 9, 12);
        assertEquals(startedWith, diamondsWithinReach(), "after the same again with what was left");

        world.advanceTicks(1);
        assertEquals(startedWith, diamondsWithinReach(), "after the region was written back to its store");

        closeByPlayer();
        assertEquals(startedWith, diamondsWithinReach(), "after the screen went away");

        //without this the count above would hold for a sequence in which nothing ever moved
        assertEquals(8, world.getSurface().getItem(10).getAmount());
        assertEquals(2, world.getSurface().getItem(11).getAmount());
        assertEquals(1, world.getSurface().getItem(12).getAmount());
        assertTrue(ItemStore.isEmpty(player.getCursor()), "and the last one came off the cursor");
        assertEquals(1, player.getPlayerInventory().getItem(0).getAmount(), "into their own inventory");
    }

    /** Every diamond the player can still reach: the editable area, their own inventory, and the cursor. */
    private int diamondsWithinReach() {
        int total = 0;
        SurfaceDouble surface = world.getSurface();
        for (int slot : AREA) {
            total += diamondsIn(surface.getItem(slot));
        }
        SurfaceDouble own = player.getPlayerInventory();
        for (int slot = 0; slot < own.getSize(); slot++) {
            total += diamondsIn(own.getItem(slot));
        }
        return total + diamondsIn(player.getCursor());
    }

    private static int diamondsIn(ItemStack item) {
        return ItemStore.isEmpty(item) || item.getType() != Material.DIAMOND ? 0 : item.getAmount();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void closingReadsTheRegionBackAndSaysItIsTheLastWord() {
        open(screen(ClickPolicy.EDIT_ALL));
        world.getSurface().placeWithoutRecording(12, diamonds(2));

        closeByPlayer();

        assertEquals(2, store.getItem(2).getAmount(), "the container is still readable when the screen goes away");
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).isLast(), "which is what a plugin that saves lazily flushes on");
        assertEquals(AREA.length, changes.get(0).getContents().size());
    }

    @Test
    void aStoreThatBreaksOnTheWayOutCostsOnlyItsOwnContents() {
        GenericInventory sound = new GenericInventory();
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.storage(Slots.of(10)).backedBy(new BrokenStore()).policy(ClickPolicy.EDIT_ALL);
        screen.storage(Slots.of(11)).backedBy(sound).policy(ClickPolicy.EDIT_ALL);
        open(screen);
        world.getSurface().placeWithoutRecording(11, diamonds(2));
        player.holding(diamonds(4));

        closeByPlayer();

        assertEquals(2, sound.getItem(0).getAmount(), "the region next to the broken one was still read back");
        assertTrue(ItemStore.isEmpty(player.getCursor()), "and what the player was holding was still taken "
                + "off the cursor");
        assertEquals(4, player.getPlayerInventory().getItem(0).getAmount(),
                "and handed back - a broken store must not cost the player what they were carrying");
    }

    /** A store that cannot be written: the region behind it fails on the way out, and only it does. */
    private static final class BrokenStore implements ItemStore {

        @Override
        public int getCapacity() {
            return 1;
        }

        @Override
        public ItemStack getItem(int slot) {
            return null;
        }

        @Override
        public void setItemSilently(int slot, ItemStack item) {
            throw new IllegalStateException("this store cannot be written");
        }

        @Override
        public int[] getOccupiedSlots() {
            return new int[0];
        }

    }

    @Test
    void whatIsLeftOnTheCursorGoesBackToThePlayer() {
        open(screen(ClickPolicy.EDIT_ALL));
        player.holding(diamonds(4));

        closeByPlayer();

        assertTrue(ItemStore.isEmpty(player.getCursor()), "taken off the cursor first, so nothing can hand out "
                + "a second copy of it");
        assertEquals(4, player.getPlayerInventory().getItem(0).getAmount(), "and it is in their inventory, "
                + "not on the ground and not gone");
    }

    @Test
    void aChangeSaysWhoseScreenItWasAndWhichStoreItWrote() {
        open(screen(ClickPolicy.EDIT_ALL));
        clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        world.advanceTicks(1);

        assertSame(player.asPlayer(), ChestScreenRenderer.playerOf(changes.get(0).getViewer()));
        assertSame(store, ((GenericInventoryStore) changes.get(0).getBacking()).getInventory(),
                "which is what tells two regions apart when one handler serves both");
    }

    @Test
    void theStoreAndTheWindowNeverShareAStack() {
        open(screen(ClickPolicy.EDIT_ALL));
        clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        world.getSurface().placeWithoutRecording(10, diamonds(2));
        world.advanceTicks(1);

        assertNotSame(store.getItem(0), world.getSurface().getItem(10));
        //the stack the handler was handed itself, not a copy of it
        changes.get(0).getContents().get(0).getNativeItem().getItemStack().setAmount(64);
        assertEquals(2, store.getItem(0).getAmount(), "what the handler was handed is a copy of a copy");
        assertEquals(2, world.getSurface().getItem(10).getAmount(), "and so is what the window shows");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declarations that cannot work
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aRegionWithNoStoreRefusesToOpenAndNamesWhatIsMissing() {
        Screen<Layout> screen = Screen.of(3);
        screen.storage(Slots.of(AREA));

        IllegalStateException failure = refusal(screen);

        assertTrue(failure.getMessage().contains("backedBy"), failure.getMessage());
    }

    @Test
    void twoRegionsCannotShareASlot() {
        Screen<Layout> screen = Screen.of(3);
        screen.storage(Slots.of(10, 11)).backedBy(store);
        screen.storage(Slots.of(11, 12)).backedBy(new GenericInventory());

        IllegalStateException failure = refusal(screen);

        assertTrue(failure.getMessage().contains("Slot 11"), failure.getMessage());
    }

    /** The open of {@code screen} is refused, and this is why. */
    private IllegalStateException refusal(Screen<Layout> screen) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> world.tryOpen(screen, player).get());
        return assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void aSecondScreenOnTheSameStoreIsReported() {
        open(screen(ClickPolicy.EDIT_ALL));
        Screen<Layout> second = Screen.of(3);
        second.storage(Slots.of(AREA)).backedBy(store).policy(ClickPolicy.EDIT_ALL);

        world.open(second, world.newPlayer("Alex"));

        assertTrue(logged("already has open"), "two screens on one store lose each other's items, and that "
                + "cannot be silent: " + loggedMessages());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Telling the button that closed from the escape key
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void wasClosedByNamesTheIconWhoseHandlerClosedTheScreen() {
        List<Boolean> saved = new ArrayList<>();
        Icon save = iconOf(Material.EMERALD);
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.icon(11, save).onClick(ClickContext::close);
        screen.onClose(context -> saved.add(context.wasClosedBy(save)));
        ScreenView view = open(screen);

        clicks.leftClick(player, 11);
        world.advanceTicks(1);

        assertTrue(view.isClosed());
        assertEquals(1, saved.size());
        assertTrue(saved.get(0));
    }

    @Test
    void aScreenNobodyClosedByHandWasClosedByNothing() {
        List<Boolean> saved = new ArrayList<>();
        Icon save = iconOf(Material.EMERALD);
        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.icon(11, save).onClick(ClickContext::close);
        screen.onClose(context -> saved.add(context.wasClosedBy(save)));
        open(screen);

        closeByPlayer();

        assertFalse(saved.get(0), "the escape key and a button both close, and only one of them is a decision");
    }

    @Test
    void theIconThatClosedIsMatchedByItsLayoutKeyAndNotByIdentity() {
        List<Boolean> saved = new ArrayList<>();
        Icon painted = iconOf(Material.EMERALD);
        painted.setName("SAVE");
        //what a layout field holds is never the copy a view drew and a click came from
        Icon declared = iconOf(Material.EMERALD);
        declared.setName("SAVE");

        Screen<Layout> screen = Screen.of(3).debounce(0);
        screen.icon(11, painted).onClick(ClickContext::close);
        screen.onClose(context -> saved.add(context.wasClosedBy(declared)));
        open(screen);

        clicks.leftClick(player, 11);
        world.advanceTicks(1);

        assertTrue(saved.get(0));
    }

    private boolean logged(String fragment) {
        for (String line : loggedMessages()) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private List<String> loggedMessages() {
        return world.getPlatform().getLoggedMessages();
    }

}
