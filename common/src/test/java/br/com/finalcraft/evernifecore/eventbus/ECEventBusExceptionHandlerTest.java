package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bus does with a subscriber or a watch callback that throws: it goes to the bus's
 * {@link ECEventExceptionHandler}, the queue behind it still runs, and whatever the handler itself
 * throws is the producer's.
 */
@ECoreTest
class ECEventBusExceptionHandlerTest {

    @Test
    void aFailingSubscriberReachesTheHandlerWithItsViewTheEventAndTheThrowableAndTheRestStillRun() {
        List<Object[]> failures = new ArrayList<>();
        ECEventBus bus = ECEventBus.create((subscription, event, failure) -> failures.add(new Object[]{subscription, event, failure}));
        List<String> order = new ArrayList<>();

        IllegalStateException broken = new IllegalStateException("this subscriber is broken on purpose");
        ECEventSubscription<SampleEvent> failing = bus.subscribe(SampleEvent.class, event -> {
            order.add("failing");
            throw broken;
        });
        bus.subscribe(SampleEvent.class, event -> order.add("after"));

        SampleEvent posted = bus.post(new SampleEvent());

        assertEquals(Arrays.asList("failing", "after"), order);
        assertEquals(1, failures.size());
        assertSame(failing, failures.get(0)[0], "the very view the subscriber was given as a handle");
        assertSame(posted, failures.get(0)[1]);
        assertSame(broken, failures.get(0)[2]);
        assertSame(ECEventExceptionHandler.LOGGING, ECEventBus.create().getExceptionHandler(), "the default of a scoped bus");
        assertSame(ECEventExceptionHandler.LOGGING, ECEventBus.global().getExceptionHandler(), "the default of the global bus");
    }

    @Test
    void whatTheHandlerThrowsPropagatesToTheProducer() {
        ECEventBus bus = ECEventBus.create((subscription, event, failure) -> {
            throw new AssertionError("subscriber failed: " + subscription, failure);
        });
        IllegalStateException broken = new IllegalStateException("broken on purpose");
        bus.subscribe(SampleEvent.class, event -> {
            throw broken;
        });

        AssertionError propagated = assertThrows(AssertionError.class, () -> bus.post(new SampleEvent()));

        assertSame(broken, propagated.getCause(), "the producer sees the handler's throw with the subscriber's failure as its cause");
    }

    @Test
    void aSubscriberMayThrowACheckedExceptionWithoutWrappingIt() {
        List<Throwable> failures = new ArrayList<>();
        ECEventBus bus = ECEventBus.create((subscription, event, failure) -> failures.add(failure));
        bus.subscribe(SampleEvent.class, event -> {
            throw new IOException("checked, and it compiles");
        });

        bus.post(new SampleEvent());

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof IOException);
    }

    @Test
    void aFailingWatchCallbackReachesTheHandlerWithTheWatchAndTheSideThatFailed() {
        List<String> failures = new ArrayList<>();
        ECEventBus bus = ECEventBus.create(new ECEventExceptionHandler() {
            @Override
            public void onSubscriberFailure(ECEventSubscription<?> subscription, IECEvent event, Throwable failure) {
                failures.add("subscriber");
            }

            @Override
            public void onWatchFailure(ECListenerWatch watch, boolean onFirstListener, Throwable failure) {
                failures.add((onFirstListener ? "first" : "gone") + " " + watch);
            }
        });
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(() -> transitions.add("first"), () -> {
            transitions.add("gone");
            throw new IllegalStateException("this watch callback is broken on purpose");
        }, SampleEvent.class);

        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        subscription.unsubscribe();

        assertEquals(Arrays.asList("first", "gone"), transitions);
        assertEquals(Collections.singletonList("gone [SampleEvent] absent"), failures,
                "the side that failed and the watch, already moved to the state it was going to");
    }

    @Test
    void theLoggingDefaultNamesTheSubscriptionAndTheEventClass() {
        ECEventBus bus = ECEventBus.create();
        ECEventSubscription<SampleEvent> failing = bus.subscribe(SampleEvent.class, event -> {
            throw new IllegalStateException("logged, not thrown");
        });

        List<String> logged = Logs.capture(() -> bus.post(new SampleEvent()));

        assertTrue(logged.stream().anyMatch(line -> line.contains(failing.toString()) && line.contains(SampleEvent.class.getName())),
                "expected a line naming " + failing + " and " + SampleEvent.class.getName() + " in " + logged);
    }

    static class SampleEvent implements IECEvent {
    }

}
