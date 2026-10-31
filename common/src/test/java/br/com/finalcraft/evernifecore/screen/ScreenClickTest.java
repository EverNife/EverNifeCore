package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a view does with a click, in the order it does it.
 *
 * <p>The order is the subject. A view that resolves the icon first and asks about the policy afterwards is a view where
 * a gesture that pushes items in goes through on a button, and where a refused click still spends the debounce window.
 * Every test here names one of those and pins the step that prevents it.</p>
 *
 * <p>It lives in the framework's own package so the debounce window can be read as arithmetic instead of waited on - a
 * test that sleeps to watch a timer expire is a slow test that still proves nothing about the boundary.</p>
 */
class ScreenClickTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;
    private final AtomicInteger handlerRuns = new AtomicInteger();
    private final List<ClickContext> seen = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private static Icon icon(String material) {
        return Icon.of(ItemSpec.of(material, 1));
    }

    /** A button whose handler only records that it ran - every test here is about whether it should have. */
    private Button button(Screen<?> screen, int slot, String material) {
        return screen.icon(slot, icon(material)).onClick(context -> {
            handlerRuns.incrementAndGet();
            seen.add(context);
        });
    }

    private boolean primary(ScreenView view, int slot, ClickKind kind) {
        return world.click(view, slot, ClickGesture.PRIMARY, kind);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Nothing moves until something says it may
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aClickOnAScreenWithNoPolicyIsCancelledAndStillRunsTheHandler() {
        Screen<?> screen = Screen.of(3);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        assertFalse(primary(view, 13, ClickKind.TAKE), "the default screen lets nothing move");
        assertEquals(1, handlerRuns.get(), "a button is a refused take whose handler still runs");
        close(view);
    }

    @Test
    void allowMoveFreesTheOneClickThatAskedForIt() {
        Screen<?> screen = Screen.of(3).debounce(0);
        screen.icon(13, icon("minecraft:diamond")).onClick(context -> {
            handlerRuns.incrementAndGet();
            context.allowMove();
        });
        button(screen, 14, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        assertTrue(primary(view, 13, ClickKind.TAKE));
        assertFalse(primary(view, 14, ClickKind.TAKE),
                "one click was freed, not the screen - the neighbouring slot still refuses");
        assertEquals(2, handlerRuns.get());
        close(view);
    }

    @Test
    void aRegionCanOpenUpItsOwnSlotsWithoutOpeningTheScreen() {
        Screen<?> screen = Screen.of(3)
                .addRegion(new NamedRegion("storage", Slots.of(10, 11), ClickPolicy.builder().allowTake().build()));
        button(screen, 0, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        assertTrue(primary(view, 10, ClickKind.TAKE));
        assertFalse(primary(view, 10, ClickKind.PLACE), "the region opened taking, not placing");
        assertFalse(primary(view, 0, ClickKind.TAKE), "a slot no region claims answers to the screen, which allows nothing");
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A click outside the screen is not a click on the screen
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aClickOutsideTheGridNeverResolvesAMenuIcon() {
        Screen<?> screen = Screen.of(3);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        //31 is where a chest renderer's raw slot lands in the inventory below; 13 + 27 collides with nothing here
        assertFalse(primary(view, 31, ClickKind.TAKE));
        assertFalse(primary(view, -1, ClickKind.TAKE));
        assertEquals(0, handlerRuns.get(), "a slot past the grid belongs to no icon of this screen");
        close(view);
    }

    @Test
    void whatWouldPushAnItemIntoTheScreenIsRefused() {
        Screen<?> screen = Screen.of(3).debounce(0);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        assertFalse(world.click(view, 13, ClickGesture.SHIFT_PRIMARY, ClickKind.MOVE_TO_OTHER_INVENTORY));
        assertFalse(world.click(view, 13, ClickGesture.DOUBLE, ClickKind.COLLECT_TO_CURSOR));
        assertFalse(world.click(view, 13, ClickGesture.NUMBER_KEY, ClickKind.HOTBAR));
        assertFalse(primary(view, 13, ClickKind.PLACE));
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What is on screen has to be what the framework drew
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aSlotShowingSomethingElseIsPutBackByAResync() {
        Screen<?> screen = Screen.of(3);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        surface.forgetApplies();

        //something outside the framework changed the slot; the buffer still believes it drew a diamond
        ((GridSurface) surface).writeSlot(13, new RenderedIcon(ItemSpec.of("minecraft:dirt", 1),
                Collections.<ItemSpec>emptyList(), Collections.<String>emptyList()));
        view.resync();

        assertEquals(1, surface.getApplies().size(), "one write puts the screen back: " + surface.getApplies());
        assertEquals(1, surface.getApplies().get(0).size(), "and only the slot that drifted is in it");
        assertEquals("minecraft:diamond", surface.getSlot(13).getItem().getMaterialId());
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Debounce
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void twoClicksInsideTheWindowRunTheHandlerOnce() {
        Screen<?> screen = Screen.of(3).debounce(Screen.DEFAULT_DEBOUNCE_MILLIS);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        primary(view, 13, ClickKind.TAKE);
        primary(view, 13, ClickKind.TAKE);

        assertEquals(1, handlerRuns.get(), "the second click is what a double click sends, not what a "
                + "player asked for twice");
        close(view);
    }

    @Test
    void aScreenWithoutDebounceAnswersEveryClick() {
        Screen<?> screen = Screen.of(3).debounce(0);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        primary(view, 13, ClickKind.TAKE);
        primary(view, 13, ClickKind.TAKE);
        primary(view, 13, ClickKind.TAKE);

        assertEquals(3, handlerRuns.get());
        close(view);
    }

    @Test
    void aRefusedAttemptDoesNotPushTheWindowFurtherOut() {
        Screen<?> screen = Screen.of(3).debounce(Screen.DEFAULT_DEBOUNCE_MILLIS);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        primary(view, 13, ClickKind.TAKE);
        long afterTheAcceptedClick = System.currentTimeMillis();

        //the burst has to straddle at least one millisecond, or "the window did not move" and "no time
        //passed" are the same observation
        while (System.currentTimeMillis() <= afterTheAcceptedClick) {
            primary(view, 13, ClickKind.TAKE);
        }

        assertEquals(1, handlerRuns.get());
        assertFalse(view.isWithinDebounce(afterTheAcceptedClick + Screen.DEFAULT_DEBOUNCE_MILLIS),
                "the window still ends where the ACCEPTED click put it; an attempt that moved it is how "
                        + "holding the mouse down locks a player out of their own menu");
        close(view);
    }

    @Test
    void theWindowIsMeasuredFromTheClickThatWasAccepted() {
        Screen<?> screen = Screen.of(3).debounce(500);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        view.markClickAccepted(1_000L);

        assertTrue(view.isWithinDebounce(1_000L));
        assertTrue(view.isWithinDebounce(1_499L));
        assertFalse(view.isWithinDebounce(1_500L), "the boundary is exclusive at the far end");
        close(view);
    }

    @Test
    void aSlotWithNoHandlerDoesNotSpendTheWindow() {
        Screen<?> screen = Screen.of(3).debounce(Screen.DEFAULT_DEBOUNCE_MILLIS);
        screen.icon(0, icon("minecraft:stone"));
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        primary(view, 0, ClickKind.TAKE);
        primary(view, 13, ClickKind.TAKE);

        assertEquals(1, handlerRuns.get(), "clicking decoration must not eat the next real click");
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A click that never finishes
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aHandlerWaitingOnSomethingThatNeverArrivesDoesNotBlockTheNextClick() {
        List<CompletableFuture<String>> pending = new ArrayList<>();
        Screen<?> screen = Screen.of(3).debounce(0);
        screen.icon(13, icon("minecraft:diamond")).onClick(context -> {
            handlerRuns.incrementAndGet();
            seen.add(context);
            CompletableFuture<String> never = new CompletableFuture<>();
            pending.add(never);
            never.thenAccept(value -> context.refresh());
        });
        ScreenView view = world.open(screen, alex);

        primary(view, 13, ClickKind.TAKE);
        primary(view, 13, ClickKind.TAKE);

        assertEquals(2, handlerRuns.get(), "the first click is still in flight and the second was answered");
        assertFalse(seen.get(0).isAlive(), "a later click makes the earlier one stale");
        assertTrue(seen.get(1).isAlive());
        assertEquals(2, pending.size());
        close(view);
    }

    @Test
    void anAnswerThatArrivesAfterTheNextClickIsDiscarded() {
        Screen<?> screen = Screen.of(3).debounce(0);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        world.advanceTicks(2);

        primary(view, 13, ClickKind.TAKE);
        ClickContext stale = seen.get(0);
        primary(view, 13, ClickKind.TAKE);
        assertFalse(stale.isAlive());

        surface.forgetApplies();
        stale.refresh();
        stale.close();
        world.advanceTicks(2);

        assertTrue(surface.getApplies().isEmpty(), "a screen the player has moved on from is not redrawn "
                + "by an answer that arrived too late: " + surface.getApplies());
        assertSame(view, Screens.getOpenView(alex), "nor closed by one");
        assertFalse(surface.isClosed());
        close(view);
    }

    @Test
    void theContextCarriesTheClickWithoutCarryingTheEvent() {
        Screen<?> screen = Screen.of(3).debounce(0);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        view.click(13, ClickGesture.SECONDARY, ClickKind.TAKE, ItemSpec.of("minecraft:emerald", 3));

        ClickContext context = seen.get(0);
        assertEquals(13, context.getSlot());
        assertEquals(ClickGesture.SECONDARY, context.getGesture());
        assertEquals(ClickKind.TAKE, context.getKind());
        assertSame(screen, context.getScreen());
        assertSame(view, context.getView());
        assertSame(alex, context.getViewer());
        assertEquals("minecraft:emerald", context.getCursor().getMaterialId());
        assertEquals(3, context.getCursor().getAmount());
        assertEquals("minecraft:diamond", context.getIcon().getItem().getMaterialId());
        assertEquals("slot#13", context.getElementId(), "a raw-slot button is addressed by its slot");
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Who may see what
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anIconTheViewerMayNotSeeIsNotPaintedAndCannotBeClicked() {
        Screen<?> screen = Screen.of(3);
        screen.icon(13, icon("minecraft:diamond").permission("gui.admin")).onClick(context -> handlerRuns.incrementAndGet());
        ScreenView view = world.open(screen, alex);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertNull(surface.getLastPatch(13), "an icon nobody may see is not drawn at all");
        assertNull(surface.getSlot(13));
        primary(view, 13, ClickKind.TAKE);
        world.click(view, "slot#13", ClickGesture.PRIMARY);
        assertEquals(0, handlerRuns.get());
        close(view);
    }

    @Test
    void anIconTheViewerMaySeeBehavesNormally() {
        alex.grant("gui.admin");
        Screen<?> screen = Screen.of(3);
        screen.icon(13, icon("minecraft:diamond").permission("gui.admin")).onClick(context -> handlerRuns.incrementAndGet());
        ScreenView view = world.open(screen, alex);

        assertEquals("minecraft:diamond", world.getRenderer().surfaceOf(view).getSlot(13).getItem().getMaterialId());
        primary(view, 13, ClickKind.TAKE);
        assertEquals(1, handlerRuns.get());
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The window is a seam, not a platform type
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aViewKnowsItsOwnContainerWhateverImplementsIt() {
        Screen<?> screen = Screen.of(3);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);
        RecordingSurface drawnOn = world.getRenderer().surfaceOf(view);

        assertTrue(view.isSurface(drawnOn));
        assertFalse(view.isSurface(new RecordingSurface(view)), "a window of the same size is still a different window");
        assertFalse(view.isSurface(null));
        close(view);
    }

    @Test
    void aClickByElementReachesTheSameHandlerAsAClickBySlot() {
        Screen<?> screen = Screen.of(3).debounce(0);
        button(screen, 13, "minecraft:diamond");
        ScreenView view = world.open(screen, alex);

        assertFalse(world.click(view, "slot#13", ClickGesture.PRIMARY), "the default screen lets nothing move, "
                + "here as anywhere");
        assertEquals(1, handlerRuns.get());
        assertSame(view, seen.get(0).getView());
        assertEquals(13, seen.get(0).getSlot(), "the element landed on the slot it painted");

        world.click(view, "slot#12", ClickGesture.PRIMARY);
        primary(view, 12, ClickKind.TAKE);
        assertEquals(1, handlerRuns.get(), "an id nothing painted, and an empty slot of the same screen, resolve no icon");
        close(view);
    }

    @Test
    void aKindNobodyClassifiedIsRefusedEvenOnAnOpenScreen() {
        Screen<?> screen = Screen.of(3).addRegion(new NamedRegion("storage", Slots.of(13),
                ClickPolicy.builder().allow(ClickKind.TAKE, ClickKind.PLACE).build()));
        ScreenView view = world.open(screen, alex);

        assertTrue(primary(view, 13, ClickKind.TAKE));
        assertFalse(world.click(view, 13, ClickGesture.MIDDLE, ClickKind.CLONE), "creative duplication was never opened up");
        assertFalse(primary(view, 13, ClickKind.UNKNOWN));
        assertFalse(world.click(view, 13, ClickGesture.UNKNOWN, ClickKind.TAKE),
                "a gesture nobody classified is refused even for a kind the region allows");
        close(view);
    }

}
