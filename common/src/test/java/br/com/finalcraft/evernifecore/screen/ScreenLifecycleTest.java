package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a screen starts costing something, and when it stops.
 *
 * <p>Both edges are leaks if they are wrong. An open the platform refused must leave nothing behind - no registration,
 * no task, no retained player - and a screen that closed must stop paying for itself immediately rather than whenever a
 * collector gets round to it. In between, a periodic redraw has to run on the period it was given: {@code every(20)}
 * once a second, not twenty times.</p>
 */
class ScreenLifecycleTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private static Icon stone() {
        return Icon.of(ItemSpec.of("minecraft:stone", 1));
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  An open that was refused
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anOpenForSomebodyWhoLeftLeavesNothingBehind() {
        TestFPlayerSender gone = world.viewer("Steve").online(false);
        //the recording renderer serves only online players by default; serving everyone reaches the open's own check
        world.getRenderer().supporting((viewer, screen) -> true);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, stone());

        CompletableFuture<ScreenView> future = world.tryOpen(screen, gone);

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause().getMessage().contains("no longer online"), failure.getCause().getMessage());
        assertEquals(0, Screens.getOpenCount());
        assertEquals(0, world.getTicks().getActiveTaskCount());
        assertTrue(world.getRenderer().getSurfaces().isEmpty(), "not even a window was asked for");
    }

    @Test
    void anOpenTheServerRefusedRegistersNothingAndSchedulesNothing() {
        TestFPlayerSender steve = world.viewer("Steve");
        world.getRenderer().refuseOpens(true);
        Screen<?> screen = Screen.of(3).addComponent(component -> {
            component.every(20);
            component.render(slots -> slots.icon(4, stone()));
        });

        CompletableFuture<ScreenView> future = world.tryOpen(screen, steve);

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause().getMessage().contains("did not open the window"), failure.getCause().getMessage());
        assertEquals(0, Screens.getOpenCount());
        assertEquals(0, world.getTicks().getActiveTaskCount(), "the periodic redraw belongs to a view that never opened");
        assertTrue(world.getRenderer().getSurfaces().isEmpty(), "and nothing was drawn anywhere");
        assertNull(Screens.getOpenView(steve));
    }

    @Test
    void aScreenThatBreaksWhileStartingTakesItsWindowBackInsteadOfLeavingASkeleton() {
        TestFPlayerSender steve = world.viewer("Steve");
        Screen<?> screen = Screen.of(3);
        //two elements under one id: refused while the view starts, which is after the window opened
        screen.icon(0, stone()).id("twin");
        screen.icon(1, stone()).id("twin");

        CompletableFuture<ScreenView> future = world.tryOpen(screen, steve);

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause().getMessage().contains("already has an element"), failure.getCause().getMessage());
        RecordingSurface window = world.getRenderer().getSurface();
        assertTrue(window.isClosed(), "the player is left looking at nothing rather than at an empty screen");
        assertNull(Screens.getOpenView(steve), "and no view is left for the renderer to route clicks to");
        assertEquals(0, Screens.getOpenCount());
        assertEquals(0, world.getTicks().getActiveTaskCount());
        assertTrue(window.getApplies().isEmpty(), "nothing was ever drawn into the window");
    }

    @Test
    void aRefusalIsLoggedSoIgnoringTheFutureIsStillSafe() {
        TestFPlayerSender steve = world.viewer("Steve");
        world.getRenderer().refuseOpens(true);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, stone());

        List<String> logged = Logs.capture(() -> world.tryOpen(screen, steve));

        assertTrue(logged.stream().anyMatch(line -> line.contains("did not open")),
                "a plugin that ignores the future still gets told: " + logged);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Nothing is scheduled globally
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aScreenWithNothingToAnimateSchedulesNothingAtAll() {
        Screen<?> screen = Screen.of(3);
        screen.icon(0, stone());
        screen.icon(8, stone());
        ScreenView view = world.open(screen, world.viewer("Steve"));

        world.advanceTicks(50);

        assertEquals(0, world.getTicks().getActiveTaskCount(), "a screen nobody is changing has no reason to wake up");
        close(view);
    }

    @Test
    void aClosedScreenLeavesNoTaskRunning() {
        AtomicInteger redraws = new AtomicInteger();
        Screen<?> screen = Screen.of(3).addComponent(component -> {
            component.watch(redraws::get);
            component.render(slots -> slots.icon(0, stone()));
        });
        screen.icon(4, Icon.of(ItemSpec.of("minecraft:clock", 1))).every(20).render(icon -> redraws.incrementAndGet());
        ScreenView view = world.open(screen, world.viewer("Steve"));

        world.advanceTicks(40);
        assertTrue(world.getTicks().getActiveTaskCount() >= 2, "while it is open there is a redraw task and a watch poll");
        int redrawsWhileOpen = redraws.get();

        view.close();

        assertEquals(0, world.getTicks().getActiveTaskCount(), "zero screens open, zero tasks");
        world.advanceTicks(200);
        assertEquals(redrawsWhileOpen, redraws.get(), "an icon nobody is looking at costs nothing");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The period is the period
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyTwentyTicksIsOncePerSecondAndNotOncePerTick() {
        AtomicInteger redraws = new AtomicInteger();
        Screen<?> screen = Screen.of(3);
        screen.icon(4, Icon.of(ItemSpec.of("minecraft:clock", 1))).every(20).render(icon -> redraws.incrementAndGet());
        ScreenView view = world.open(screen, world.viewer("Steve"));
        assertEquals(1, redraws.get(), "drawn once as it opened");
        redraws.set(0);

        //each period marks the icon dirty and the pass that redraws it runs on the tick after
        world.advanceTicks(101);

        assertEquals(5, redraws.get());
        close(view);
    }

    @Test
    void aComponentRedrawsOnItsOwnPeriod() {
        AtomicInteger renders = new AtomicInteger();
        Screen<?> screen = Screen.of(3).addComponent(component -> {
            component.every(20);
            component.render(slots -> {
                renders.incrementAndGet();
                slots.icon(4, stone());
            });
        });
        ScreenView view = world.open(screen, world.viewer("Steve"));

        assertEquals(1, renders.get(), "drawn once as it opened");

        world.advanceTicks(101);

        assertEquals(6, renders.get());
        close(view);
    }

    @Test
    void anAnimatedIconIsCopiedPerViewerSoOneScreenCannotRestyleAnother() {
        Screen<?> screen = Screen.of(3);
        Button clock = screen.icon(4, Icon.of(ItemSpec.of("minecraft:clock", 1)))
                .every(20)
                .render(icon -> icon.amount(icon.getItem().getAmount() + 1));

        ScreenView first = world.open(screen, world.viewer("Steve"));
        world.advanceTicks(60);
        ScreenView second = world.open(screen, world.viewer("Alex"));

        assertEquals(1, clock.getIcon().getItem().getAmount(), "the declared icon is never the one being animated");
        assertEquals(4, world.getRenderer().surfaceOf(first).getSlot(4).getItem().getAmount(),
                "the first viewer drew the opening frame and two periods on top of the declaration");
        assertEquals(2, world.getRenderer().surfaceOf(second).getSlot(4).getItem().getAmount(),
                "the second viewer starts from the declaration plus its own opening frame, not from the first "
                        + "viewer's frame");
        close(first);
        close(second);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void closingUnregistersTheScreenAndLetsThePlayerGo() {
        Screen<?> screen = Screen.of(3);
        screen.icon(0, stone());
        ScreenView view = world.open(screen, world.viewer("Steve"));

        assertEquals(1, Screens.getOpenCount());
        assertNotNull(view.getViewer());

        world.getRenderer().surfaceOf(view).playerCloses();

        assertTrue(view.isClosed());
        assertEquals(0, Screens.getOpenCount());
        assertNull(view.getViewer(), "the player is released there and then, not left to a collector");
        assertEquals("Steve", view.getViewerName(), "who it was is still readable - the reference is not");
    }

    @Test
    void disconnectingRunsOnCloseToo() {
        List<CloseReason> reasons = new ArrayList<>();
        TestFPlayerSender steve = world.viewer("Steve");
        Screen<?> screen = Screen.of(3).onClose(context -> reasons.add(context.getReason()));
        screen.icon(0, stone());
        world.open(screen, steve);

        Screens.viewerGone(steve.getUniqueId(), CloseReason.DISCONNECTED);

        assertEquals(Arrays.asList(CloseReason.DISCONNECTED), reasons);
        assertEquals(0, Screens.getOpenCount());
    }

    @Test
    void theShutdownSweepClosesEveryScreenAndRunsEachOnClose() {
        List<CloseReason> reasons = new ArrayList<>();
        List<String> stillShown = new ArrayList<>();
        Screen<?> screen = Screen.of(3).onClose(context -> {
            reasons.add(context.getReason());
            RenderedIcon shown = ((GridSurface) context.getView().getSurface()).read(0);
            stillShown.add(shown == null ? "empty" : shown.getItem().getMaterialId());
        });
        screen.icon(0, stone());

        world.open(screen, world.viewer("Steve"));
        world.open(screen, world.viewer("Alex"));
        assertEquals(2, Screens.getOpenCount());

        Screens.closeAll();

        assertEquals(0, Screens.getOpenCount());
        assertEquals(Arrays.asList(CloseReason.SHUTDOWN, CloseReason.SHUTDOWN), reasons);
        assertEquals(Arrays.asList("minecraft:stone", "minecraft:stone"), stillShown,
                "the window is still readable inside onClose, which is what makes it the place to hand back "
                        + "whatever the screen was holding");
        for (RecordingSurface window : world.getRenderer().getSurfaces()) {
            assertEquals(CloseReason.SHUTDOWN, window.getClosedWith(), "and each window is taken away after its onClose");
        }
    }

    @Test
    void aSecondCloseChangesNothing() {
        AtomicInteger closes = new AtomicInteger();
        TestFPlayerSender steve = world.viewer("Steve");
        Screen<?> screen = Screen.of(3).onClose(context -> closes.incrementAndGet());
        screen.icon(0, stone());
        ScreenView view = world.open(screen, steve);

        world.getRenderer().surfaceOf(view).playerCloses();
        Screens.closeAll();
        Screens.viewerGone(steve.getUniqueId(), CloseReason.DISCONNECTED);

        assertEquals(1, closes.get(), "the teardown is idempotent, so onClose fires once");
    }

}
