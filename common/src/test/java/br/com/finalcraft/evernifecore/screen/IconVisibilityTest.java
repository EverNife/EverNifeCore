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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A button that says when it is alive, and the screen finding that out on its own.
 *
 * <p>The interesting party in each of these is the one nobody talks to: no click, no {@code refresh()}, no state anyone
 * on the screen was told about - just a value out in the plugin that moves, and a slot that has to notice. A screen that
 * only notices because some other button of it happens to be polling is a screen whose correctness depends on its
 * neighbours.</p>
 *
 * <p>The cadence is asserted on its own screen every time, because a look is what these tests measure: one clock task
 * serves the whole view, so a button polling once a tick next to one polling once a second is the only proof that the
 * interval belongs to the watch and not to the view.</p>
 */
class IconVisibilityTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender player;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        player = world.viewer("Steve");
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private static Icon emerald() {
        return Icon.of(ItemSpec.of("minecraft:emerald", 1));
    }

    private static Icon goldIngot() {
        return Icon.of(ItemSpec.of("minecraft:gold_ingot", 1));
    }

    private static String materialAt(RecordingSurface surface, int slot) {
        RenderedIcon shown = surface.getSlot(slot);
        return shown == null ? null : shown.getItem().getMaterialId();
    }

    /** Runs the pass the open scheduled, so the task count below is the view's standing tasks and nothing else. */
    private void settle() {
        world.advanceTicks(1);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The default: the screen looks by itself
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anIconAloneOnTheScreenStillFollowsAChangeMadeOutsideIt() {
        AtomicBoolean sold = new AtomicBoolean(false);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, emerald()).visibleWhen(() -> !sold.get());

        ScreenView view = world.open(screen, player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals("minecraft:emerald", materialAt(surface, 0));

        sold.set(true); //another player bought it: nothing on this screen was told, and nothing refreshes it
        world.advanceTicks(2);

        assertNull(surface.getSlot(0), "the only button on the screen has to be enough to arm the look - "
                + "with the poll coming from a neighbouring button, this slot would still be for sale");
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The cadence, which belongs to the watch
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void twoIconsOnOneScreenKeepTheirOwnCadenceOffTheSameWheel() {
        AtomicBoolean sold = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, emerald()).visibleWhen(() -> !sold.get());
        screen.icon(1, goldIngot()).visibleWhen(() -> !closed.get(), 20);

        ScreenView view = world.open(screen, player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(); //tick 1: both watches were polled once

        assertEquals(1, world.getTicks().getActiveTaskCount(),
                "two watches at two cadences, and one task for both of them");

        sold.set(true);
        closed.set(true);
        world.advanceTicks(2); //tick 3: the fast watch saw it on tick 2, its pass ran on tick 3

        assertNull(surface.getSlot(0), "the one that looks every tick is already gone");
        assertEquals("minecraft:gold_ingot", materialAt(surface, 1),
                "and the one that looks once a second has not looked yet");

        world.advanceTicks(17); //tick 20: the slow look happens here, and the write is the pass after it

        assertEquals("minecraft:gold_ingot", materialAt(surface, 1));

        world.advanceTicks(1);

        assertNull(surface.getSlot(1), "one interval later the slower one agrees");
        assertEquals(1, world.getTicks().getActiveTaskCount(), "still one task");
        assertNull(view.getIconAt(1), "and the click follows the picture, as ever");
        close(view);
    }

    @Test
    void aCadenceOfZeroPutsNothingOnTheClockAndWaitsToBeTold() {
        AtomicBoolean sold = new AtomicBoolean(false);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, emerald()).visibleWhen(() -> !sold.get(), 0);

        ScreenView view = world.open(screen, player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle();

        assertEquals(0, world.getTicks().getActiveTaskCount(), "nothing was put on the clock");

        sold.set(true);
        world.advanceTicks(60);

        assertEquals("minecraft:emerald", materialAt(surface, 0),
                "a cadence of zero is a promise the screen will not look, three seconds included");

        view.refresh();
        world.advanceTicks(1);

        assertNull(surface.getSlot(0), "and being told still works");
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The expensive predicate: declared trigger instead of a poll
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aPredicateThatIsNeverPolledRunsOnceForEachStateItDependsOn() {
        MutableState<Integer> blacklistVersion = State.of(0);
        AtomicBoolean blacklisted = new AtomicBoolean(false);
        AtomicInteger asked = new AtomicInteger();

        Screen<?> screen = Screen.of(3);
        screen.icon(0, emerald())
                .visibleWhen(() -> {
                    asked.incrementAndGet();
                    return !blacklisted.get();
                }, 0)
                .dependsOn(blacklistVersion);

        ScreenView view = world.open(screen, player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals(1, asked.get(), "the render asked, and nothing else did - a watch would have asked too");
        assertEquals("minecraft:emerald", materialAt(surface, 0));

        blacklisted.set(true);
        blacklistVersion.set(1); //the plugin says the list moved; the predicate itself is never polled
        world.advanceTicks(1);

        assertNull(surface.getSlot(0));
        assertEquals(2, asked.get(), "one look per change, on the render the state asked for");
        assertEquals(0, world.getTicks().getActiveTaskCount());

        blacklistVersion.set(2);
        world.advanceTicks(1);

        assertEquals(3, asked.get(), "and the next change costs exactly one more");
        close(view);
    }

    @Test
    void aDeclaredDependencyRedrawsTheIconWithoutAnyPredicateAtAll() {
        MutableState<Boolean> lit = State.of(false);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, emerald()).visibleWhen(lit::get, 0).dependsOn(lit);

        ScreenView view = world.open(screen, player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertNull(surface.getSlot(0), "a predicate answering false hides the icon from the first render");

        lit.set(true);
        world.advanceTicks(1);

        assertNotNull(surface.getSlot(0), "the state the icon depends on is the whole trigger");
        close(view);
    }

}
