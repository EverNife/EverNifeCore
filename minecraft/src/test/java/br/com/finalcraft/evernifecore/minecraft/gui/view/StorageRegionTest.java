package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickKind;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.ClickSimulator;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;
    private GenericInventory store;
    private final List<StorageContext> changes = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
        store = new GenericInventory();
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    /** A screen whose middle four slots are editable under {@code policy}. */
    private Gui<LayoutBase> screen(ClickPolicy policy) {
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(AREA)).backedBy(store).policy(policy).onChange(changes::add);
        return gui;
    }

    private GuiView open(Gui<?> gui) {
        return world.openDetachedAndRegistered(gui, player);
    }

    private static ItemStack diamonds(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
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
        Gui<LayoutBase> gui = screen(ClickPolicy.EDIT_ALL);
        gui.icon(Slots.of(10, 11), Icon.of(new ItemStack(Material.STONE)).background());
        GuiView view = open(gui);
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.DIAMOND, surface.getItem(10).getType(), "the region's item is what shows");
        assertNull(surface.getItem(11), "and an empty region slot stays empty instead of showing the icon");
        assertNull(view.getIconAt(10), "a slot the player owns carries no icon to dispatch");
    }

    @Test
    void aResyncLeavesTheRegionAlone() {
        GuiView view = open(screen(ClickPolicy.EDIT_ALL));
        SurfaceDouble surface = world.getSurface();
        surface.placeWithoutRecording(11, diamonds(1));

        view.resync();

        assertEquals(Material.DIAMOND, surface.getItem(11).getType(), "putting the screen back cannot blank a "
                + "slot the framework stopped drawing - that would be the player's item destroyed");
    }

    @Test
    void aReplacedContainerKeepsWhatTheRegionHeld() {
        GuiView view = open(screen(ClickPolicy.EDIT_ALL));
        world.getSurface().placeWithoutRecording(11, diamonds(3));

        view.adoptSurface(new SurfaceDouble(27));

        assertEquals(3, view.getSurface().getItem(11).getAmount(), "a title change costs a new container, "
                + "and the framework does not redraw these slots - it has to carry them across");
    }

    @Test
    void theStoreIsOnlyReadAsFarAsTheRegionReaches() {
        store.setItem(0, diamonds(1));
        store.setItem(9, new ItemStack(Material.DIRT));
        GuiView view = open(screen(ClickPolicy.EDIT_ALL));

        assertEquals(1, world.getSurface().getWrittenSlots().size(), "only the indexes the region reaches "
                + "were drawn: " + world.getSurface().getWrites());
        assertTrue(logged("past that"), "and the plugin is told the rest is out of reach: " + loggedMessages());

        world.closeDetached(view);
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
        Gui<LayoutBase> gui = screen(ClickPolicy.EDIT_ALL);
        gui.icon(0, Icon.of(new ItemStack(Material.STONE)).onClick(context -> runs.incrementAndGet()));
        open(gui);

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
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .denyPlace(item -> item.getType() == Material.DIRT)
                .onChange(changes::add);
        open(gui);

        player.holding(new ItemStack(Material.DIRT));
        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());

        player.holding(diamonds(1));
        assertFalse(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled(),
                "the filter is about the item, not about the gesture");
    }

    @Test
    void anAllowPlaceFilterLetsNothingElseIn() {
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .allowPlace(item -> item.getType() == Material.DIAMOND);
        open(gui);

        player.holding(new ItemStack(Material.DIRT));
        assertTrue(clicks.click(player, 10, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());

        player.holding(diamonds(1));
        assertFalse(clicks.click(player, 10, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());
    }

    @Test
    void aSwapIsJudgedByWhatTheCursorWouldLeaveBehind() {
        store.setItem(0, diamonds(1));
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .denyPlace(item -> item.getType() == Material.DIRT);
        open(gui);

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
        //raw slot 27 is the player's first slot, which their own inventory numbers 9
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(5));

        InventoryClickEvent event = clicks.clickPlayerInventory(player, 0, ClickType.SHIFT_LEFT,
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

        InventoryClickEvent event = clicks.clickPlayerInventory(player, 0, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertTrue(event.isCancelled());
        assertNull(world.getSurface().getItem(10));
        assertEquals(5, player.getPlayerInventory().getItem(9).getAmount(), "nothing left the source either");
    }

    @Test
    void aRegionWithNoRoomTakesNothingMore() {
        store.setItem(0, new ItemStack(Material.DIRT));
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(10)).backedBy(store).policy(ClickPolicy.EDIT_ALL).onChange(changes::add);
        open(gui);
        player.getPlayerInventory().placeWithoutRecording(9, diamonds(5));

        clicks.clickPlayerInventory(player, 0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

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
    void aDragOfAnItemTheFilterRefusesIsRefusedWhole() {
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(AREA))
                .backedBy(store)
                .policy(ClickPolicy.EDIT_ALL)
                .denyPlace(item -> item.getType() == Material.DIRT);
        open(gui);
        ItemStack dragged = new ItemStack(Material.DIRT, 2);
        player.holding(dragged);

        InventoryDragEvent event = clicks.dragEvenly(player, dragged, 10, 11);

        assertTrue(event.isCancelled());
        assertNull(world.getSurface().getItem(10));
        assertEquals(2, player.getCursor().getAmount(), "the cursor kept all of it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void closingReadsTheRegionBackAndSaysItIsTheLastWord() {
        GuiView view = open(screen(ClickPolicy.EDIT_ALL));
        world.getSurface().placeWithoutRecording(12, diamonds(2));

        world.closeDetached(view);

        assertEquals(2, store.getItem(2).getAmount(), "the container is still readable when the screen goes away");
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).isLast(), "which is what a plugin that saves lazily flushes on");
        assertEquals(AREA.length, changes.get(0).getContents().size());
    }

    @Test
    void whatIsLeftOnTheCursorGoesBackToThePlayer() {
        GuiView view = open(screen(ClickPolicy.EDIT_ALL));
        player.holding(diamonds(4));

        world.closeDetached(view);

        assertTrue(GuiBuffer.isEmpty(player.getCursor()), "taken off the cursor first, so nothing can hand out "
                + "a second copy of it");
        assertEquals(4, player.getPlayerInventory().getItem(0).getAmount(), "and it is in their inventory, "
                + "not on the ground and not gone");
    }

    @Test
    void aChangeSaysWhoseScreenItWasAndWhichStoreItWrote() {
        open(screen(ClickPolicy.EDIT_ALL));
        clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        world.advanceTicks(1);

        assertSame(player.asPlayer(), changes.get(0).getViewer());
        assertSame(store, changes.get(0).getBacking(), "which is what tells two regions apart when one "
                + "handler serves both");
    }

    @Test
    void theStoreAndTheWindowNeverShareAStack() {
        open(screen(ClickPolicy.EDIT_ALL));
        clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        world.getSurface().placeWithoutRecording(10, diamonds(2));
        world.advanceTicks(1);

        assertNotSame(store.getItem(0), world.getSurface().getItem(10));
        changes.get(0).getContents().get(0).setAmount(64);
        assertEquals(2, store.getItem(0).getAmount(), "what the handler was handed is a copy of a copy");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declarations that cannot work
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aRegionWithNoStoreRefusesToOpenAndNamesWhatIsMissing() {
        Gui<LayoutBase> gui = Gui.of(3);
        gui.storage(Slots.of(AREA));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> open(gui));

        assertTrue(failure.getMessage().contains("backedBy"), failure.getMessage());
    }

    @Test
    void twoRegionsCannotShareASlot() {
        Gui<LayoutBase> gui = Gui.of(3);
        gui.storage(Slots.of(10, 11)).backedBy(store);
        gui.storage(Slots.of(11, 12)).backedBy(new GenericInventory());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> open(gui));

        assertTrue(failure.getMessage().contains("Slot 11"), failure.getMessage());
    }

    @Test
    void aSecondScreenOnTheSameStoreIsReported() {
        open(screen(ClickPolicy.EDIT_ALL));
        Gui<LayoutBase> second = Gui.of(3);
        second.storage(Slots.of(AREA)).backedBy(store).policy(ClickPolicy.EDIT_ALL);

        world.openDetachedAndRegistered(second, world.newPlayer("Alex"));

        assertTrue(logged("already has open"), "two screens on one store lose each other's items, and that "
                + "cannot be silent: " + loggedMessages());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Telling the button that closed from the escape key
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void wasClosedByNamesTheIconWhoseHandlerClosedTheScreen() {
        List<Boolean> saved = new ArrayList<>();
        Icon save = Icon.of(new ItemStack(Material.EMERALD)).onClick(ClickContext::close);
        Gui<LayoutBase> gui = Gui.of(3).debounce(0).icon(11, save);
        gui.onClose(context -> saved.add(context.wasClosedBy(save)));
        GuiView view = open(gui);

        clicks.leftClick(player, 11);
        world.advanceTicks(1);

        assertTrue(view.isClosed());
        assertEquals(1, saved.size());
        assertTrue(saved.get(0));
    }

    @Test
    void aScreenNobodyClosedByHandWasClosedByNothing() {
        List<Boolean> saved = new ArrayList<>();
        Icon save = Icon.of(new ItemStack(Material.EMERALD)).onClick(ClickContext::close);
        Gui<LayoutBase> gui = Gui.of(3).debounce(0).icon(11, save);
        gui.onClose(context -> saved.add(context.wasClosedBy(save)));
        GuiView view = open(gui);

        world.closeDetached(view);

        assertFalse(saved.get(0), "the escape key and a button both close, and only one of them is a decision");
    }

    @Test
    void theIconThatClosedIsMatchedByItsLayoutKeyAndNotByIdentity() {
        List<Boolean> saved = new ArrayList<>();
        Icon painted = Icon.of(new ItemStack(Material.EMERALD)).onClick(ClickContext::close);
        painted.setName("SAVE");
        //what a layout field holds is never the copy a view drew and a click came from
        Icon declared = Icon.of(new ItemStack(Material.EMERALD));
        declared.setName("SAVE");

        Gui<LayoutBase> gui = Gui.of(3).debounce(0).icon(11, painted);
        gui.onClose(context -> saved.add(context.wasClosedBy(declared)));
        open(gui);

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
