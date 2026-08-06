package br.com.finalcraft.evernifecore.minecraft.gui.state;

import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * When a state says it went stale, and when it stays quiet.
 *
 * <p>A written state always says so, even when the new value equals the old one - the far end of the
 * pipeline deduplicates on rendered output, and an object mutated in place cannot prove it changed.
 * A watched state is the opposite: it says so only when its key moved, because it is polled every
 * tick and would otherwise repaint the screen forever.</p>
 */
class StateTest {

    private static class Counter implements Runnable {

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void run() {
            count.incrementAndGet();
        }

        int get() {
            return count.get();
        }

    }

    /** A domain object the plugin mutates instead of replacing - the case {@code watch} has to be told about. */
    private static final class Arena {

        private int players = 0;

        void join() {
            players++;
        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    //  MutableState
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aWrittenStateInvalidatesEvenWhenTheValueIsTheSame() {
        MutableState<String> state = State.of("same");
        Counter counter = new Counter();
        state.addListener(counter);

        state.set("same");
        state.set("same");

        assertEquals(2, counter.get(), "equality is not evidence of no change - a mutated object equals itself");
    }

    @Test
    void updateReadsTheCurrentValueAndWritesTheNext() {
        MutableState<Integer> state = State.of(1);
        Counter counter = new Counter();
        state.addListener(counter);

        state.update(value -> value + 1);
        state.update(value -> value * 10);

        assertEquals(20, state.get());
        assertEquals(2, counter.get());
    }

    @Test
    void cancellingASubscriptionStopsTheCallbacks() {
        MutableState<Integer> state = State.of(0);
        Counter counter = new Counter();
        Cancellable subscription = state.addListener(counter);

        state.set(1);
        subscription.cancel();
        state.set(2);
        subscription.cancel();

        assertEquals(1, counter.get(), "cancelling twice is a no-op, not a second removal");
        assertEquals(2, state.get());
    }

    @Test
    void aNullListenerIsAnInertSubscription() {
        MutableState<Integer> state = State.of(0);
        assertSame(Cancellable.NONE, state.addListener(null));
    }

    @Test
    void aListenerThatThrowsDoesNotSilenceTheOthers() {
        MutableState<Integer> state = State.of(0);
        List<String> heard = new ArrayList<>();

        state.addListener(() -> heard.add("first"));
        state.addListener(() -> {
            throw new IllegalStateException("this one is broken");
        });
        state.addListener(() -> heard.add("third"));

        state.set(1);

        assertEquals(Arrays.asList("first", "third"), heard);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  WatchState
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aWatchIsQuietWhileItsKeyStandsStill() {
        MutableState<String> source = State.of("a");
        WatchState<String> watch = new WatchState<>(source::get, value -> value);
        Counter counter = new Counter();
        watch.addListener(counter);

        watch.poll();
        watch.poll();

        assertEquals(0, counter.get(), "polling an unchanged value costs nothing");
        assertEquals("a", watch.get());
    }

    @Test
    void aWatchSpeaksOnceWhenItsKeyMoves() {
        MutableState<String> source = State.of("a");
        WatchState<String> watch = new WatchState<>(source::get, value -> value);
        Counter counter = new Counter();
        watch.addListener(counter);

        source.set("b");
        watch.poll();
        watch.poll();

        assertEquals(1, counter.get());
        assertEquals("b", watch.get());
    }

    @Test
    void anObjectMutatedInPlaceIsInvisibleWithoutAKey() {
        Arena arena = new Arena();
        WatchState<Arena> blind = new WatchState<>(() -> arena, value -> value);
        WatchState<Arena> keyed = new WatchState<>(() -> arena, value -> value.players);
        Counter blindCounter = new Counter();
        Counter keyedCounter = new Counter();
        blind.addListener(blindCounter);
        keyed.addListener(keyedCounter);

        arena.join();
        blind.poll();
        keyed.poll();

        assertEquals(0, blindCounter.get(), "the same instance equals itself, so the default key never moves");
        assertEquals(1, keyedCounter.get(), "a key that moves with the object is what reports it");
    }

    @Test
    void aWatchHandlesAValueThatIsAbsent() {
        MutableState<String> source = State.of(null);
        WatchState<String> watch = new WatchState<>(source::get, value -> value);
        Counter counter = new Counter();
        watch.addListener(counter);

        assertNull(watch.get());
        source.set("here");
        watch.poll();
        source.set(null);
        watch.poll();

        assertEquals(2, counter.get());
        assertNull(watch.get());
    }

}
