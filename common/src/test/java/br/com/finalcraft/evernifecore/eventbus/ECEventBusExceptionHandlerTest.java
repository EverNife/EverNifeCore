package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.testing.FailingExceptionHandler;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.RecordingExceptionHandler;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bus does with a subscriber or a watch callback that throws: it goes to the bus's
 * {@link ECEventExceptionHandler}, the queue behind it still runs, and whatever the handler itself
 * throws is the producer's - which is how the engine's {@link FailingExceptionHandler} fails a test.
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
    void theEnginesFailingHandlerFailsThePostWithTheSubscribersFailureAsTheCause() {
        ECEventBus bus = ECEventBus.create(new FailingExceptionHandler());
        IllegalStateException broken = new IllegalStateException("broken on purpose");
        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
            throw broken;
        });

        AssertionError failed = assertThrows(AssertionError.class, () -> bus.post(new SampleEvent()));

        assertSame(broken, failed.getCause());
        assertTrue(failed.getMessage().contains(subscription.toString()), failed.getMessage());
        assertTrue(failed.getMessage().contains(SampleEvent.class.getName()), failed.getMessage());
    }

    @Test
    void underECoreTestABrokenSubscriberOnTheGlobalBusFailsTheTest() {
        assertTrue(ECEventBus.global().getExceptionHandler() instanceof FailingExceptionHandler,
                "@ECoreTest makes the global bus strict for the length of the class: " + ECEventBus.global().getExceptionHandler());

        IllegalStateException broken = new IllegalStateException("broken on purpose, on the global bus");
        ECEventSubscription<SampleEvent> subscription = ECEventBus.global().subscribe(SampleEvent.class, event -> {
            throw broken;
        });
        try {
            AssertionError failed = assertThrows(AssertionError.class, () -> ECEventBus.global().post(new SampleEvent()));
            assertSame(broken, failed.getCause());
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    void installExceptionHandlerSwapsTheHandlerAndHandsBackThePreviousOne() {
        ECEventBus bus = ECEventBus.create();
        RecordingExceptionHandler recording = new RecordingExceptionHandler();

        ECEventExceptionHandler previous = EventBuses.installExceptionHandler(bus, recording);

        assertSame(ECEventExceptionHandler.LOGGING, previous);
        assertSame(recording, bus.getExceptionHandler());
        assertSame(recording, EventBuses.installExceptionHandler(bus, previous), "the swap back hands the recording one back");
        assertSame(ECEventExceptionHandler.LOGGING, bus.getExceptionHandler());
    }

    @Test
    void aSubscriberMayThrowACheckedExceptionWithoutWrappingIt() {
        RecordingExceptionHandler recording = new RecordingExceptionHandler();
        ECEventBus bus = ECEventBus.create(recording);
        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
            throw new IOException("checked, and it compiles");
        });

        SampleEvent posted = bus.post(new SampleEvent());

        assertEquals(1, recording.getFailureCount());
        RecordingExceptionHandler.Failure failure = recording.getFailures().get(0);
        assertTrue(failure.getThrowable() instanceof IOException);
        assertSame(subscription, failure.getSubscription());
        assertSame(posted, failure.getEvent());
        assertNull(failure.getWatch());
        recording.reset();
        assertEquals(0, recording.getFailureCount());
    }

    @Test
    void aFailingWatchCallbackReachesTheHandlerWithTheWatchAndTheSideThatFailed() {
        RecordingExceptionHandler recording = new RecordingExceptionHandler();
        ECEventBus bus = ECEventBus.create(recording);
        List<String> transitions = new ArrayList<>();
        ECListenerWatch watch = bus.watchListeners(() -> transitions.add("first"), () -> {
            transitions.add("gone");
            throw new IllegalStateException("this watch callback is broken on purpose");
        }, SampleEvent.class);

        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        subscription.unsubscribe();

        assertEquals(Arrays.asList("first", "gone"), transitions);
        assertEquals(1, recording.getFailureCount());
        RecordingExceptionHandler.Failure failure = recording.getFailures().get(0);
        assertSame(watch, failure.getWatch());
        assertFalse(failure.isOnFirstListener(), "it was the last-listener-gone side that broke");
        assertNull(failure.getSubscription());
        assertEquals("[SampleEvent] absent", watch.toString(), "already moved to the state it was going to");
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
