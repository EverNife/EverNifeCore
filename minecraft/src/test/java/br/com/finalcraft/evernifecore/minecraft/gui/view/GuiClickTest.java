package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickKind;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.ClickSimulator;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the framework does with a click, in the order it does it.
 *
 * <p>The order is the subject. A screen that resolves the icon first and asks about the policy
 * afterwards is a screen where a click in the player's own inventory can trigger a menu button,
 * where a shift-click empties the menu into the player, and where a slot that no longer shows what
 * the framework drew still runs the handler of what it used to show. Every test here names one of
 * those and pins the step that prevents it.</p>
 *
 * <p>It lives in the framework's own package so the debounce window can be read as arithmetic
 * instead of waited on - a test that sleeps to watch a timer expire is a slow test that still
 * proves nothing about the boundary.</p>
 */
class GuiClickTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;
    private final AtomicInteger handlerRuns = new AtomicInteger();
    private final List<ClickContext> seen = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    /** An icon whose handler only records that it ran - every test here is about whether it should have. */
    private Icon button(Material material) {
        return Icon.of(new ItemStack(material)).onClick(context -> {
            handlerRuns.incrementAndGet();
            seen.add(context);
        });
    }

    private GuiView open(Gui gui) {
        return world.open(gui, player);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Nothing moves until something says it may
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aClickOnAScreenWithNoPolicyIsCancelledAndStillRunsTheHandler() {
        open(Gui.of(3).icon(13, button(Material.DIAMOND)));

        InventoryClickEvent event = clicks.leftClick(player, 13);

        assertTrue(event.isCancelled(), "the default screen lets nothing move");
        assertEquals(1, handlerRuns.get(), "a button is a refused take whose handler still runs");
    }

    @Test
    void allowMoveFreesTheOneClickThatAskedForIt() {
        Icon permissive = Icon.of(new ItemStack(Material.DIAMOND)).onClick(context -> {
            handlerRuns.incrementAndGet();
            context.allowMove();
        });
        open(Gui.of(3).debounce(0).icon(13, permissive).icon(14, button(Material.DIAMOND)));

        assertFalse(clicks.leftClick(player, 13).isCancelled());
        assertTrue(clicks.leftClick(player, 14).isCancelled(),
                "one click was freed, not the screen - the neighbouring slot still refuses");
        assertEquals(2, handlerRuns.get());
    }

    @Test
    void aRegionCanOpenUpItsOwnSlotsWithoutOpeningTheScreen() {
        Gui<?> gui = Gui.of(3)
                .icon(0, button(Material.DIAMOND))
                .addRegion(new Region("storage", Slots.of(10, 11),
                        ClickPolicy.builder().allowTake().build()));
        open(gui);

        assertFalse(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertTrue(clicks.click(player, 10, ClickType.LEFT, InventoryAction.PLACE_ONE).isCancelled(),
                "the region opened taking, not placing");
        assertTrue(clicks.click(player, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled(),
                "a slot no region claims answers to the screen, which allows nothing");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A click outside the screen is not a click on the screen
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aClickInThePlayerInventoryNeverResolvesAMenuIcon() {
        open(Gui.of(3).icon(13, button(Material.DIAMOND)));

        InventoryClickEvent event = clicks.clickPlayerInventory(player, 4, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        assertEquals(31, event.getRawSlot(), "past the end of the screen, so it landed in the player");
        assertEquals(13, event.getSlot(), "and the local slot number collides exactly with the button - "
                + "the two containers of a view number their slots independently");
        assertEquals(0, handlerRuns.get(), "reading getSlot() here would have fired the menu's button");
        assertFalse(event.isCancelled(), "and the player's own inventory is none of the framework's business");
    }

    @Test
    void theTwoActionsThatReachIntoTheScreenFromOutsideAreJudgedByItsPolicy() {
        open(Gui.of(3).icon(4, button(Material.DIAMOND)));

        assertTrue(clicks.clickPlayerInventory(player, 4, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertTrue(clicks.clickPlayerInventory(player, 4, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        assertFalse(clicks.clickPlayerInventory(player, 4, ClickType.LEFT,
                InventoryAction.PICKUP_ALL).isCancelled(), "everything else there stays the player's own");
        assertEquals(0, handlerRuns.get(), "judged, never dispatched");
    }

    @Test
    void whatWouldPushAnItemIntoTheScreenIsRefused() {
        open(Gui.of(3).icon(13, button(Material.DIAMOND)));

        assertTrue(clicks.click(player, 13, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                .isCancelled());
        assertTrue(clicks.click(player, 13, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR)
                .isCancelled());
        assertTrue(clicks.click(player, 13, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP).isCancelled());
        assertTrue(clicks.click(player, 13, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
    }

    @Test
    void aDragThatTouchesTheScreenIsRefusedWholesale() {
        open(Gui.of(3).icon(13, button(Material.DIAMOND)));
        ItemStack dragged = new ItemStack(Material.DIRT);

        assertTrue(clicks.drag(player, dragged, 13).isCancelled());
        assertTrue(clicks.drag(player, dragged, 30, 13).isCancelled(),
                "vanilla spreads one drag over both containers, so touching the screen refuses the gesture");
        assertFalse(clicks.drag(player, dragged, 30, 31).isCancelled(),
                "a drag entirely inside the player's own inventory is left alone");
    }

    @Test
    void aRegionThatAllowsDraggingStillCannotBeDraggedIntoWithoutAStore() {
        Gui<?> gui = Gui.of(3).addRegion(new Region("storage", Slots.of(13),
                ClickPolicy.builder().allowDrag().build()));
        open(gui);

        assertTrue(clicks.drag(player, new ItemStack(Material.DIRT), 13).isCancelled(),
                "the screen still draws that slot, so an item left there is one the next render erases - "
                        + "an area a player may really drag into is declared with storage(...)");
        assertTrue(clicks.drag(player, new ItemStack(Material.DIRT), 12, 13).isCancelled(),
                "one slot of the gesture outside the region refuses all of it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What is on screen has to be what the framework drew
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aSlotShowingSomethingElseIsRefusedAndPutBack() {
        open(Gui.of(3).icon(13, button(Material.DIAMOND)));
        SurfaceDouble surface = world.getSurface();
        surface.forgetWrites();

        //something outside the framework changed the slot; the buffer still believes it drew a diamond
        surface.placeWithoutRecording(13, new ItemStack(Material.DIRT));
        InventoryClickEvent event = clicks.leftClick(player, 13);

        assertTrue(event.isCancelled());
        assertEquals(0, handlerRuns.get(), "the click was aimed at something that is no longer there");
        assertEquals(1, surface.getWriteCount(), "and the screen is put back: " + surface.getWrites());
        assertEquals(Material.DIAMOND, surface.getItem(13).getType());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Debounce
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void twoClicksInsideTheWindowRunTheHandlerOnce() {
        open(Gui.of(3).debounce(Gui.DEFAULT_DEBOUNCE_MILLIS).icon(13, button(Material.DIAMOND)));

        clicks.leftClick(player, 13);
        clicks.leftClick(player, 13);

        assertEquals(1, handlerRuns.get(), "the second click is what a double click sends, not what a "
                + "player asked for twice");
    }

    @Test
    void aScreenWithoutDebounceAnswersEveryClick() {
        open(Gui.of(3).debounce(0).icon(13, button(Material.DIAMOND)));

        clicks.leftClick(player, 13);
        clicks.leftClick(player, 13);
        clicks.leftClick(player, 13);

        assertEquals(3, handlerRuns.get());
    }

    @Test
    void aRefusedAttemptDoesNotPushTheWindowFurtherOut() {
        GuiView view = open(Gui.of(3).debounce(Gui.DEFAULT_DEBOUNCE_MILLIS).icon(13, button(Material.DIAMOND)));

        clicks.leftClick(player, 13);
        long afterTheAcceptedClick = System.currentTimeMillis();

        //the burst has to straddle at least one millisecond, or "the window did not move" and "no time
        //passed" are the same observation
        while (System.currentTimeMillis() <= afterTheAcceptedClick) {
            clicks.leftClick(player, 13);
        }

        assertEquals(1, handlerRuns.get());
        assertFalse(view.isWithinDebounce(afterTheAcceptedClick + Gui.DEFAULT_DEBOUNCE_MILLIS),
                "the window still ends where the ACCEPTED click put it; an attempt that moved it is how "
                        + "holding the mouse down locks a player out of their own menu");
    }

    @Test
    void theWindowIsMeasuredFromTheClickThatWasAccepted() {
        GuiView view = open(Gui.of(3).debounce(500).icon(13, button(Material.DIAMOND)));

        view.markClickAccepted(1_000L);

        assertTrue(view.isWithinDebounce(1_000L));
        assertTrue(view.isWithinDebounce(1_499L));
        assertFalse(view.isWithinDebounce(1_500L), "the boundary is exclusive at the far end");
    }

    @Test
    void aSlotWithNoHandlerDoesNotSpendTheWindow() {
        Gui<?> gui = Gui.of(3)
                .debounce(Gui.DEFAULT_DEBOUNCE_MILLIS)
                .icon(0, Icon.of(new ItemStack(Material.STONE)))
                .icon(13, button(Material.DIAMOND));
        open(gui);

        clicks.leftClick(player, 0);
        clicks.leftClick(player, 13);

        assertEquals(1, handlerRuns.get(), "clicking decoration must not eat the next real click");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A click that never finishes
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aHandlerWaitingOnSomethingThatNeverArrivesDoesNotBlockTheNextClick() {
        List<CompletableFuture<String>> pending = new ArrayList<>();
        Icon icon = Icon.of(new ItemStack(Material.DIAMOND)).onClick(context -> {
            handlerRuns.incrementAndGet();
            seen.add(context);
            CompletableFuture<String> never = new CompletableFuture<>();
            pending.add(never);
            never.thenAccept(value -> context.refresh());
        });
        open(Gui.of(3).debounce(0).icon(13, icon));

        clicks.leftClick(player, 13);
        clicks.leftClick(player, 13);

        assertEquals(2, handlerRuns.get(), "the first click is still in flight and the second was answered");
        assertFalse(seen.get(0).isAlive(), "a later click makes the earlier one stale");
        assertTrue(seen.get(1).isAlive());
        assertEquals(2, pending.size());
    }

    @Test
    void anAnswerThatArrivesAfterTheNextClickIsDiscarded() {
        open(Gui.of(3).debounce(0).icon(13, button(Material.DIAMOND)));
        SurfaceDouble surface = world.getSurface();

        clicks.leftClick(player, 13);
        ClickContext stale = seen.get(0);
        clicks.leftClick(player, 13);
        assertFalse(stale.isAlive());

        surface.forgetWrites();
        stale.refresh();
        stale.close();

        assertEquals(0, surface.getWriteCount(), "a screen the player has moved on from is not redrawn "
                + "by an answer that arrived too late");
        assertNotNull(GuiViews.getOpenView(player.asPlayer()), "nor closed by one");
        assertNotNull(player.getOpenView());
    }

    @Test
    void theContextCarriesTheClickWithoutCarryingTheEvent() {
        player.holding(new ItemStack(Material.EMERALD, 3));
        Gui<?> gui = Gui.of(3).debounce(0).icon(13, button(Material.DIAMOND));
        GuiView view = open(gui);

        clicks.click(player, 13, ClickType.RIGHT, InventoryAction.PICKUP_HALF);

        ClickContext context = seen.get(0);
        assertEquals(13, context.getSlot());
        assertEquals(ClickType.RIGHT, context.getClickType());
        assertSame(gui, context.getGui());
        assertSame(view, context.getView());
        assertSame(player.asPlayer(), context.getViewer());
        assertEquals(Material.EMERALD, context.getCursor().getType());
        assertEquals(3, context.getCursor().getAmount());
        assertEquals(Material.DIAMOND, context.getIcon().getItemStack().getType());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Who may see what
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anIconTheViewerMayNotSeeIsNotPaintedAndCannotBeClicked() {
        Icon restricted = button(Material.DIAMOND).permission("gui.admin");
        open(Gui.of(3).icon(13, restricted));
        SurfaceDouble surface = world.getSurface();

        assertEquals(0, surface.getWriteCount(), "an icon nobody may see is not drawn at all");
        clicks.leftClick(player, 13);
        assertEquals(0, handlerRuns.get());
    }

    @Test
    void anIconTheViewerMaySeeBehavesNormally() {
        player.withPermission("gui.admin");
        open(Gui.of(3).icon(13, button(Material.DIAMOND).permission("gui.admin")));

        assertEquals(Material.DIAMOND, world.getSurface().getItem(13).getType());
        clicks.leftClick(player, 13);
        assertEquals(1, handlerRuns.get());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The container is a seam, not a Bukkit type
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aViewKnowsItsOwnContainerWhateverImplementsIt() {
        GuiView view = world.openDetached(Gui.of(3).icon(13, button(Material.DIAMOND)), player);
        SurfaceDouble drawnOn = world.getSurface();

        assertTrue(view.isSurface(drawnOn.asInventory()));
        assertFalse(view.isSurface(new SurfaceDouble(27).asInventory()),
                "a container of the same size is still a different window");
        assertFalse(view.isSurface(null));
    }

    @Test
    void aClickReachesAScreenDrawnOnAContainerTheServerNeverMade() {
        GuiView view = world.openDetachedAndRegistered(
                Gui.of(3).debounce(0).icon(13, button(Material.DIAMOND)), player);

        InventoryClickEvent event = clicks.leftClick(player, 13);

        assertTrue(event.isCancelled(), "the default screen lets nothing move, here as anywhere");
        assertEquals(1, handlerRuns.get());
        assertSame(view, seen.get(0).getView());

        clicks.leftClick(player, 12);
        assertEquals(1, handlerRuns.get(), "an empty slot of the same screen still resolves no icon");
    }

    @Test
    void aKindNobodyClassifiedIsRefusedEvenOnAnOpenScreen() {
        Gui<?> gui = Gui.of(3).addRegion(new Region("storage", Slots.of(13),
                ClickPolicy.builder().allow(ClickKind.TAKE, ClickKind.PLACE).build()));
        open(gui);

        assertFalse(clicks.click(player, 13, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertTrue(clicks.click(player, 13, ClickType.MIDDLE, InventoryAction.CLONE_STACK).isCancelled(),
                "creative duplication was never opened up");
        assertTrue(clicks.click(player, 13, ClickType.LEFT, InventoryAction.UNKNOWN).isCancelled());
    }

}
