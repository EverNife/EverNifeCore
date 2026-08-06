package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually reaches the container when a screen changes.
 *
 * <p>Every assertion here counts <b>writes</b>, not contents. A screen that redraws itself correctly
 * but writes every slot on every tick looks identical in a screenshot and is a different program: it
 * is the write that costs a packet, that fights the player's cursor, and that the whole layer/diff
 * machinery exists to avoid.</p>
 */
class GuiRenderDiffTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static Icon paper(int amount) {
        return Icon.of(new ItemStack(Material.PAPER, amount));
    }

    private static Icon stone() {
        return Icon.of(new ItemStack(Material.STONE));
    }

    /** Runs the tick the open scheduled, then makes "from here on" mean "from here on". */
    private void settle(SurfaceDouble... surfaces) {
        world.advanceTicks(1);
        for (SurfaceDouble surface : surfaces) {
            surface.forgetWrites();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Only what changed, and only where
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aStateChangeRewritesOnlyTheSlotsOfTheComponentThatReadsIt() {
        MutableState<Integer> counter = State.of(1);
        Gui gui = Gui.of(3)
                .component(c -> {
                    c.remember(counter);
                    c.render(slots -> slots.icon(0, paper(counter.get())));
                })
                .component(c -> c.render(slots -> slots.icon(8, stone())));

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        settle(surface);

        counter.set(7);
        world.advanceTicks(1);

        assertEquals(Collections.singleton(0), surface.getWrittenSlots(),
                "the second component reads nothing that changed, so its slot is not touched: "
                        + surface.getWrites());
        assertEquals(7, surface.getItem(0).getAmount());
    }

    @Test
    void aRenderThatDrawsTheSamePictureCostsNoWrite() {
        MutableState<String> unrelated = State.of("a");
        Gui gui = Gui.of(3)
                .icon(4, stone())
                .component(c -> {
                    c.remember(unrelated);
                    c.render(slots -> slots.icon(13, paper(1)));
                });

        GuiView view = world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        settle(surface);

        view.refresh();
        world.advanceTicks(1);
        unrelated.set("b");
        world.advanceTicks(1);

        assertEquals(0, surface.getWriteCount(),
                "the output is identical, so the diff writes nothing: " + surface.getWrites());
    }

    @Test
    void anObjectMutatedInPlaceStillRepaintsWhenItIsWatchedByAKey() {
        AtomicInteger score = new AtomicInteger(1);
        Gui gui = Gui.of(3).component(c -> {
            State<AtomicInteger> watched = c.watch(() -> score, AtomicInteger::get);
            c.render(slots -> slots.icon(11, paper(watched.get().get())));
        });

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        settle(surface);

        score.set(5);
        world.advanceTicks(2); //one tick polls the watch, the next runs the pass it dirtied

        assertEquals(1, surface.getWriteCount(), surface.getWrites().toString());
        assertEquals(5, surface.getItem(11).getAmount());
    }

    @Test
    void refreshRepaintsAnObjectNoKeyCouldHaveReported() {
        AtomicInteger score = new AtomicInteger(1);
        Gui gui = Gui.of(3).component(c -> c.render(slots -> slots.icon(11, paper(score.get()))));

        GuiView view = world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        settle(surface);

        score.set(9);
        world.advanceTicks(2);
        assertEquals(0, surface.getWriteCount(), "nothing was watching, so nothing knew");

        view.refresh();
        world.advanceTicks(1);

        assertEquals(1, surface.getWriteCount());
        assertEquals(9, surface.getItem(11).getAmount());
    }

    @Test
    void everyChangeMadeInOneTickCostsOneWritePerSlot() {
        MutableState<Integer> counter = State.of(1);
        Gui gui = Gui.of(3).component(c -> {
            c.remember(counter);
            c.render(slots -> slots.icon(4, paper(counter.get())));
        });

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        settle(surface);

        counter.set(2);
        counter.set(3);
        counter.set(4);
        world.advanceTicks(1);

        assertEquals(1, surface.getWriteCount(4),
                "three changes in one tick are one pass and one write: " + surface.getWrites());
        assertEquals(4, surface.getItem(4).getAmount(), "and the write carries the last value");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Depth
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void clearingAComponentUncoversTheBackgroundInsteadOfEmptyingTheSlot() {
        MutableState<Boolean> showing = State.of(true);
        Gui gui = Gui.of(3)
                .icon(Slots.all(), Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)).background())
                .component(c -> {
                    c.remember(showing);
                    c.render(slots -> {
                        if (showing.get()) {
                            slots.icon(13, paper(1));
                        }
                    });
                });

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        assertEquals(Material.PAPER, surface.getItem(13).getType());
        settle(surface);

        showing.set(false);
        world.advanceTicks(1);

        assertEquals(1, surface.getWriteCount(13));
        assertNotNull(surface.getItem(13), "the slot is never blanked while a layer below it has an item");
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, surface.getItem(13).getType());
    }

    @Test
    void aSlotWithNothingUnderneathIsClearedRatherThanFilledWithAir() {
        MutableState<Boolean> showing = State.of(true);
        Gui gui = Gui.of(3).component(c -> {
            c.remember(showing);
            c.render(slots -> {
                if (showing.get()) {
                    slots.icon(13, paper(1));
                }
            });
        });

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        settle(surface);

        showing.set(false);
        world.advanceTicks(1);

        assertEquals(1, surface.getWriteCount(13));
        assertTrue(surface.getWrites().get(0).item == null, "an empty slot is cleared, not filled with AIR");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The main thread
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aCommitFromAnotherThreadIsRefusedAndSaysWhichThreadTried() throws InterruptedException {
        GuiView view = world.openDetached(Gui.of(3).icon(0, stone()), world.newPlayer("Steve"));

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
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The title
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aTitleChangeReopensTheWindowOnceAndTheScreenSurvivesIt() {
        MutableState<Integer> page = State.of(1);
        List<MutableState<Integer>> perViewer = new ArrayList<>();
        Gui gui = Gui.of(3)
                .title(() -> "Page " + page.get())
                .component(c -> {
                    MutableState<Integer> visits = c.remember(7);
                    perViewer.add(visits);
                    c.remember(page);
                    c.render(slots -> {
                        slots.icon(4, paper(page.get()));
                        slots.icon(0, paper(visits.get()));
                    });
                });

        GuiView view = world.openDetached(gui, world.newPlayer("Steve"));
        settle(world.getSurface());
        assertEquals(1, world.getCreatedSurfaces().size());
        assertEquals(1, perViewer.size());

        page.set(2);
        world.advanceTicks(1);

        assertEquals(2, world.getCreatedSurfaces().size(), "one reopen, and exactly one");
        assertEquals("Page 2", view.getCurrentTitle());
        assertEquals(1, perViewer.size(),
                "the container was replaced, the screen was not - the component was not declared again");

        SurfaceDouble replacement = world.getSurface();
        assertEquals(Material.PAPER, replacement.getItem(4).getType(),
                "the replacement container starts empty, so everything is drawn again");
        assertEquals(2, replacement.getItem(4).getAmount());
        assertEquals(7, replacement.getItem(0).getAmount(), "and the state it was drawn from is the same state");

        page.set(3);
        world.advanceTicks(1);
        assertEquals(3, world.getCreatedSurfaces().size(), "a second change costs a second reopen, not two");
    }

    @Test
    void aTitleThatDoesNotChangeCostsNoReopen() {
        MutableState<Integer> counter = State.of(1);
        Gui gui = Gui.of(3)
                .title("Fixed")
                .component(c -> {
                    c.remember(counter);
                    c.render(slots -> slots.icon(4, paper(counter.get())));
                });

        world.openDetached(gui, world.newPlayer("Steve"));
        settle(world.getSurface());

        counter.set(2);
        world.advanceTicks(1);
        counter.set(3);
        world.advanceTicks(1);

        assertEquals(1, world.getCreatedSurfaces().size());
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
            return new Cancellable() {
                private boolean cancelled = false;

                @Override
                public void cancel() {
                    if (!cancelled) {
                        cancelled = true;
                        liveSubscriptions.decrementAndGet();
                        subscription.cancel();
                    }
                }
            };
        }

        private void set(T value) {
            delegate.set(value);
        }

    }

    @Test
    void aStateRememberedByValueBelongsToOneViewerAlone() {
        List<MutableState<Integer>> perViewer = new ArrayList<>();
        Gui gui = Gui.of(3).component(c -> {
            MutableState<Integer> own = c.remember(1);
            perViewer.add(own);
            c.render(slots -> slots.icon(0, paper(own.get())));
        });

        world.openDetached(gui, world.newPlayer("Steve"));
        world.openDetached(gui, world.newPlayer("Alex"));
        SurfaceDouble steve = world.getCreatedSurfaces().get(0);
        SurfaceDouble alex = world.getCreatedSurfaces().get(1);
        settle(steve, alex);

        assertEquals(2, perViewer.size(), "the declaration runs once per viewer");
        perViewer.get(0).set(5);
        world.advanceTicks(1);

        assertEquals(1, steve.getWriteCount(0));
        assertEquals(5, steve.getItem(0).getAmount());
        assertEquals(0, alex.getWriteCount(), "the other screen reads a different state and is untouched");
        assertEquals(1, alex.getItem(0).getAmount());
    }

    @Test
    void aStateCreatedOutsideIsSharedAndMovesEveryScreenThatRemembersIt() {
        CountingState<Integer> shared = new CountingState<>(1);
        Gui gui = Gui.of(3).component(c -> {
            c.remember(shared);
            c.render(slots -> slots.icon(8, paper(shared.get())));
        });

        world.openDetached(gui, world.newPlayer("Steve"));
        world.openDetached(gui, world.newPlayer("Alex"));
        SurfaceDouble steve = world.getCreatedSurfaces().get(0);
        SurfaceDouble alex = world.getCreatedSurfaces().get(1);
        settle(steve, alex);

        assertEquals(2, shared.liveSubscriptions.get());
        shared.set(4);
        world.advanceTicks(1);

        assertEquals(1, steve.getWriteCount(8));
        assertEquals(1, alex.getWriteCount(8));
        assertEquals(4, steve.getItem(8).getAmount());
        assertEquals(4, alex.getItem(8).getAmount());
    }

    @Test
    void closingOneScreenDropsItsSubscriptionAndLeavesTheStateToTheOther() {
        CountingState<Integer> shared = new CountingState<>(1);
        Gui gui = Gui.of(3).component(c -> {
            c.remember(shared);
            c.render(slots -> slots.icon(8, paper(shared.get())));
        });

        GuiView steve = world.openDetached(gui, world.newPlayer("Steve"));
        world.openDetached(gui, world.newPlayer("Alex"));
        SurfaceDouble steveSurface = world.getCreatedSurfaces().get(0);
        SurfaceDouble alexSurface = world.getCreatedSurfaces().get(1);
        settle(steveSurface, alexSurface);

        world.closeDetached(steve);
        assertEquals(1, shared.liveSubscriptions.get(), "the closed screen stopped listening");

        shared.set(9);
        world.advanceTicks(1);

        assertEquals(0, steveSurface.getWriteCount());
        assertEquals(1, alexSurface.getWriteCount(8));
        assertEquals(9, shared.get(), "the state outlives the screen that was reading it");
    }

}
