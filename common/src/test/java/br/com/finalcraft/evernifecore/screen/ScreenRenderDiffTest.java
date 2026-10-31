package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.RecordingRenderer;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TickScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually reaches the surface when a screen changes.
 *
 * <p>Every assertion here counts <b>writes</b>, not contents. A screen that redraws itself correctly but writes every
 * slot on every tick looks identical in a screenshot and is a different program: it is the write that costs a packet,
 * that fights the player's cursor, and that the whole layer/diff machinery exists to avoid.</p>
 */
class ScreenRenderDiffTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;

    @AfterEach
    void teardown() {
        if (world != null) {
            world.close();
        }
    }

    private ScreenWorld world() {
        world = ScreenWorld.open(tempDir);
        return world;
    }

    private static Icon paper(int amount) {
        return Icon.of(ItemSpec.of("minecraft:paper", amount));
    }

    private static Icon stone() {
        return Icon.of(ItemSpec.of("minecraft:stone", 1));
    }

    /** Runs the tick the open scheduled, then makes "from here on" mean "from here on". */
    private void settle(RecordingSurface... surfaces) {
        world.advanceTicks(1);
        for (RecordingSurface surface : surfaces) {
            surface.forgetApplies();
        }
    }

    private void closeAll(ScreenView... views) {
        for (ScreenView view : views) {
            view.close();
        }
        world.advanceTicks(2);
    }

    /** Every patch the surface received since it last forgot, across all applies. */
    private static List<ElementPatch> writes(RecordingSurface surface) {
        List<ElementPatch> all = new ArrayList<>();
        for (List<ElementPatch> apply : surface.getApplies()) {
            all.addAll(apply);
        }
        return all;
    }

    private static Set<Integer> writtenSlots(RecordingSurface surface) {
        Set<Integer> slots = new LinkedHashSet<>();
        for (ElementPatch patch : writes(surface)) {
            slots.add(patch.getSlot());
        }
        return slots;
    }

    private static int writesTo(RecordingSurface surface, int slot) {
        int count = 0;
        for (ElementPatch patch : writes(surface)) {
            if (patch.getSlot() == slot) {
                count++;
            }
        }
        return count;
    }

    private static int amountAt(RecordingSurface surface, int slot) {
        RenderedIcon shown = surface.getSlot(slot);
        assertNotNull(shown, "slot " + slot + " shows nothing");
        return shown.getItem().getAmount();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Only what changed, and only where
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aStateChangeRewritesOnlyTheSlotsOfTheComponentThatReadsIt() {
        world();
        MutableState<Integer> counter = State.of(1);
        Screen<?> screen = Screen.of(3)
                .addComponent(c -> {
                    c.remember(counter);
                    c.render(slots -> slots.icon(0, paper(counter.get())));
                })
                .addComponent(c -> c.render(slots -> slots.icon(8, stone())));

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        counter.set(7);
        world.advanceTicks(1);

        assertEquals(Collections.singleton(0), writtenSlots(surface),
                "the second component reads nothing that changed, so its slot is not touched: " + surface.getApplies());
        assertEquals(1, surface.getApplies().size(), "and the change costs one apply");
        assertEquals(7, amountAt(surface, 0));
        closeAll(view);
    }

    @Test
    void aRenderThatDrawsTheSamePictureCostsNoWrite() {
        world();
        MutableState<String> unrelated = State.of("a");
        Screen<?> screen = Screen.of(3);
        screen.icon(4, stone());
        screen.addComponent(c -> {
            c.remember(unrelated);
            c.render(slots -> slots.icon(13, paper(1)));
        });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        view.refresh();
        world.advanceTicks(1);
        unrelated.set("b");
        world.advanceTicks(1);

        assertTrue(surface.getApplies().isEmpty(),
                "the output is identical, so the diff writes nothing: " + surface.getApplies());
        closeAll(view);
    }

    @Test
    void anObjectMutatedInPlaceStillRepaintsWhenItIsWatchedByAKey() {
        world();
        AtomicInteger score = new AtomicInteger(1);
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            State<AtomicInteger> watched = c.watch(() -> score, AtomicInteger::get);
            c.render(slots -> slots.icon(11, paper(watched.get().get())));
        });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        score.set(5);
        world.advanceTicks(2); //one tick polls the watch, the next runs the pass it dirtied

        assertEquals(1, writes(surface).size(), surface.getApplies().toString());
        assertEquals(5, amountAt(surface, 11));
        closeAll(view);
    }

    @Test
    void refreshRepaintsAnObjectNoKeyCouldHaveReported() {
        world();
        AtomicInteger score = new AtomicInteger(1);
        Screen<?> screen = Screen.of(3).addComponent(c -> c.render(slots -> slots.icon(11, paper(score.get()))));

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        score.set(9);
        world.advanceTicks(2);
        assertTrue(surface.getApplies().isEmpty(), "nothing was watching, so nothing knew");

        view.refresh();
        world.advanceTicks(1);

        assertEquals(1, writes(surface).size());
        assertEquals(9, amountAt(surface, 11));
        closeAll(view);
    }

    @Test
    void everyChangeMadeInOneTickCostsOneWritePerSlot() {
        world();
        MutableState<Integer> counter = State.of(1);
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            c.remember(counter);
            c.render(slots -> slots.icon(4, paper(counter.get())));
        });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        counter.set(2);
        counter.set(3);
        counter.set(4);
        world.advanceTicks(1);

        assertEquals(1, writesTo(surface, 4),
                "three changes in one tick are one pass and one write: " + surface.getApplies());
        assertEquals(4, amountAt(surface, 4), "and the write carries the last value");
        closeAll(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Depth
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void clearingAComponentUncoversTheBackgroundInsteadOfEmptyingTheSlot() {
        world();
        MutableState<Boolean> showing = State.of(true);
        Screen<?> screen = Screen.of(3);
        screen.icon(Slots.all(), Icon.of(ItemSpec.of("minecraft:gray_stained_glass_pane", 1)).background());
        screen.addComponent(c -> {
            c.remember(showing);
            c.render(slots -> {
                if (showing.get()) {
                    slots.icon(13, paper(1));
                }
            });
        });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        assertEquals("minecraft:paper", surface.getSlot(13).getItem().getMaterialId());
        settle(surface);

        showing.set(false);
        world.advanceTicks(1);

        assertEquals(1, writesTo(surface, 13));
        assertNotNull(surface.getSlot(13), "the slot is never blanked while a layer below it has an item");
        assertEquals("minecraft:gray_stained_glass_pane", surface.getSlot(13).getItem().getMaterialId());
        closeAll(view);
    }

    @Test
    void aSlotWithNothingUnderneathIsClearedRatherThanFilledWithAir() {
        world();
        MutableState<Boolean> showing = State.of(true);
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            c.remember(showing);
            c.render(slots -> {
                if (showing.get()) {
                    slots.icon(13, paper(1));
                }
            });
        });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        showing.set(false);
        world.advanceTicks(1);

        assertEquals(1, writesTo(surface, 13));
        assertNull(surface.getLastPatch(13).getIcon(), "an empty slot is cleared, not filled with air");
        assertNull(surface.getSlot(13));
        closeAll(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The renderer thread
    // -----------------------------------------------------------------------------------------------------------------

    /** A renderer whose thread is the one that built it, so a commit from anywhere else is visibly off-thread. */
    private static final class ThreadBoundRenderer extends RecordingRenderer {

        private final ScreenScheduler bound;

        private ThreadBoundRenderer(TickScheduler ticks) {
            super("test:thread-bound", 10, ticks);
            Thread renderThread = Thread.currentThread();
            this.bound = new ScreenScheduler() {
                @Override
                public Cancellable later(long ticksLater, Runnable task) {
                    return ticks.later(ticksLater, task);
                }

                @Override
                public Cancellable repeat(long period, Runnable task) {
                    return ticks.repeat(period, task);
                }

                @Override
                public boolean isRenderThread() {
                    return Thread.currentThread() == renderThread;
                }
            };
        }

        @Override
        public ScreenScheduler getScheduler() {
            return bound;
        }

    }

    @Test
    void aCommitFromAnotherThreadIsRefusedAndSaysWhichThreadTried() throws InterruptedException {
        world();
        world.withRenderer(new ThreadBoundRenderer(world.getTicks()));
        Screen<?> screen = Screen.of(3);
        screen.icon(0, stone());
        ScreenView view = world.open(screen, world.viewer("Steve"));
        assertEquals("test:thread-bound", view.getRenderer().getId(), "the open has to land on the bound renderer");

        AtomicReference<Throwable> refusal = new AtomicReference<>();
        Thread offender = new Thread(() -> {
            try {
                view.commitNow();
            } catch (Throwable e) {
                refusal.set(e);
            }
        }, "a-plugin-worker");
        offender.start();
        offender.join();

        assertTrue(refusal.get() instanceof IllegalStateException, "got " + refusal.get());
        assertTrue(refusal.get().getMessage().contains("a-plugin-worker"),
                "the message has to name the thread that tried: " + refusal.get().getMessage());
        closeAll(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The title
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aTitleChangeIsSentOnceAndTheScreenSurvivesIt() {
        world = ScreenWorld.open(tempDir, RendererCapability.TITLE_CHANGE);
        MutableState<Integer> page = State.of(1);
        List<MutableState<Integer>> perViewer = new ArrayList<>();
        Screen<?> screen = Screen.of(3)
                .title(() -> "Page " + page.get())
                .addComponent(c -> {
                    MutableState<Integer> visits = c.remember(7);
                    perViewer.add(visits);
                    c.remember(page);
                    c.render(slots -> {
                        slots.icon(4, paper(page.get()));
                        slots.icon(0, paper(visits.get()));
                    });
                });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);
        assertEquals(Collections.singletonList("Page 1"), surface.getTitles());
        assertEquals(1, perViewer.size());

        page.set(2);
        world.advanceTicks(1);

        assertEquals(2, surface.getTitles().size(), "one title change, and exactly one: " + surface.getTitles());
        assertEquals("Page 2", view.getCurrentTitle());
        assertEquals(1, world.getRenderer().getSurfaces().size(), "the title changes on the window it already has");
        assertEquals(1, perViewer.size(), "the screen was not built again - the component was not declared twice");
        assertEquals(2, amountAt(surface, 4));
        assertEquals(7, amountAt(surface, 0), "and the state it was drawn from is the same state");

        page.set(3);
        world.advanceTicks(1);
        assertEquals(3, surface.getTitles().size(), "a second change costs a second title, not two");
        closeAll(view);
    }

    @Test
    void aTitleThatDoesNotChangeCostsNoReopen() {
        world = ScreenWorld.open(tempDir, RendererCapability.TITLE_CHANGE);
        MutableState<Integer> counter = State.of(1);
        Screen<?> screen = Screen.of(3)
                .title("Fixed")
                .addComponent(c -> {
                    c.remember(counter);
                    c.render(slots -> slots.icon(4, paper(counter.get())));
                });

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        settle(surface);

        counter.set(2);
        world.advanceTicks(1);
        counter.set(3);
        world.advanceTicks(1);

        assertEquals(Collections.singletonList("Fixed"), surface.getTitles(), "the passes ran, the title never moved");
        assertEquals(1, world.getRenderer().getSurfaces().size());
        assertEquals(3, amountAt(surface, 4), "the passes really did run");
        closeAll(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Two viewers of the same screen
    // -----------------------------------------------------------------------------------------------------------------

    /** A {@link State} that also says how many subscriptions are alive - the only way to see a leak. */
    private static final class CountingState<T> implements State<T> {

        private final MutableState<T> delegate;
        private final AtomicInteger liveSubscriptions = new AtomicInteger();

        private CountingState(T initial) {
            this.delegate = State.of(initial);
        }

        @Override
        public T get() {
            return delegate.get();
        }

        @Override
        public Cancellable addListener(Runnable onInvalidate) {
            liveSubscriptions.incrementAndGet();
            Cancellable subscription = delegate.addListener(onInvalidate);
            AtomicInteger cancelled = new AtomicInteger();
            return () -> {
                if (cancelled.getAndIncrement() == 0) {
                    liveSubscriptions.decrementAndGet();
                    subscription.cancel();
                }
            };
        }

        private void set(T value) {
            delegate.set(value);
        }

    }

    @Test
    void aStateRememberedByValueBelongsToOneViewerAlone() {
        world();
        List<MutableState<Integer>> perViewer = new ArrayList<>();
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            MutableState<Integer> own = c.remember(1);
            perViewer.add(own);
            c.render(slots -> slots.icon(0, paper(own.get())));
        });

        ScreenView steveView = world.open(screen, world.viewer("Steve"));
        ScreenView alexView = world.open(screen, world.viewer("Alex"));
        RecordingSurface steve = world.getRenderer().surfaceOf(steveView);
        RecordingSurface alex = world.getRenderer().surfaceOf(alexView);
        settle(steve, alex);

        assertEquals(2, perViewer.size(), "the declaration runs once per viewer");
        perViewer.get(0).set(5);
        world.advanceTicks(1);

        assertEquals(1, writesTo(steve, 0));
        assertEquals(5, amountAt(steve, 0));
        assertTrue(alex.getApplies().isEmpty(), "the other screen reads a different state and is untouched");
        assertEquals(1, amountAt(alex, 0));
        closeAll(steveView, alexView);
    }

    @Test
    void aStateCreatedOutsideIsSharedAndMovesEveryScreenThatRemembersIt() {
        world();
        CountingState<Integer> shared = new CountingState<>(1);
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            c.remember(shared);
            c.render(slots -> slots.icon(8, paper(shared.get())));
        });

        ScreenView steveView = world.open(screen, world.viewer("Steve"));
        ScreenView alexView = world.open(screen, world.viewer("Alex"));
        RecordingSurface steve = world.getRenderer().surfaceOf(steveView);
        RecordingSurface alex = world.getRenderer().surfaceOf(alexView);
        settle(steve, alex);

        assertEquals(2, shared.liveSubscriptions.get());
        shared.set(4);
        world.advanceTicks(1);

        assertEquals(1, writesTo(steve, 8));
        assertEquals(1, writesTo(alex, 8));
        assertEquals(4, amountAt(steve, 8));
        assertEquals(4, amountAt(alex, 8));
        closeAll(steveView, alexView);
        assertEquals(0, shared.liveSubscriptions.get(), "both closed, so nobody listens any more");
    }

    @Test
    void closingOneScreenDropsItsSubscriptionAndLeavesTheStateToTheOther() {
        world();
        CountingState<Integer> shared = new CountingState<>(1);
        Screen<?> screen = Screen.of(3).addComponent(c -> {
            c.remember(shared);
            c.render(slots -> slots.icon(8, paper(shared.get())));
        });

        TestFPlayerSender steve = world.viewer("Steve");
        ScreenView steveView = world.open(screen, steve);
        ScreenView alexView = world.open(screen, world.viewer("Alex"));
        RecordingSurface steveSurface = world.getRenderer().surfaceOf(steveView);
        RecordingSurface alexSurface = world.getRenderer().surfaceOf(alexView);
        settle(steveSurface, alexSurface);

        closeAll(steveView);
        assertTrue(steveView.isClosed());
        assertEquals(1, shared.liveSubscriptions.get(), "the closed screen stopped listening");

        shared.set(9);
        world.advanceTicks(1);

        assertTrue(steveSurface.getApplies().isEmpty());
        assertEquals(1, writesTo(alexSurface, 8));
        assertEquals(9, shared.get(), "the state outlives the screen that was reading it");
        closeAll(alexView);
    }

}
