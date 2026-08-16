package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventExceptionHandler;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The exception handler that writes every failure down and throws nothing, for the test whose point
 * is a subscriber or a watch callback that breaks: what the bus did next is asserted on the state,
 * what it was handed is asserted here.
 *
 * <pre>{@code
 * RecordingExceptionHandler recording = new RecordingExceptionHandler();
 * ECEventBus bus = ECEventBus.create(recording);
 * bus.subscribe(SampleEvent.class, event -> { throw new IllegalStateException("broken on purpose"); });
 * bus.post(new SampleEvent());
 * assertEquals(1, recording.getFailureCount());
 * }</pre>
 */
public final class RecordingExceptionHandler implements ECEventExceptionHandler {

    /** One failure the bus reported: from a subscriber (then {@link #getSubscription()} and {@link #getEvent()} answer) or from a watch callback (then {@link #getWatch()} does). */
    public static final class Failure {
        private final ECEventSubscription<?> subscription;
        private final IECEvent event;
        private final ECListenerWatch watch;
        private final boolean onFirstListener;
        private final Throwable throwable;

        Failure(ECEventSubscription<?> subscription, IECEvent event, ECListenerWatch watch, boolean onFirstListener, Throwable throwable) {
            this.subscription = subscription;
            this.event = event;
            this.watch = watch;
            this.onFirstListener = onFirstListener;
            this.throwable = throwable;
        }

        /** The subscription that threw, or {@code null} for a watch failure. */
        public ECEventSubscription<?> getSubscription() {
            return subscription;
        }

        /** The event being delivered, or {@code null} for a watch failure. */
        public IECEvent getEvent() {
            return event;
        }

        /** The watch whose callback threw, or {@code null} for a subscriber failure. */
        public ECListenerWatch getWatch() {
            return watch;
        }

        /** For a watch failure, whether it was the first-listener side that threw (the last-listener-gone side otherwise). */
        public boolean isOnFirstListener() {
            return onFirstListener;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        @Override
        public String toString() {
            return subscription != null
                    ? "subscriber " + subscription + " on " + event.getClass().getSimpleName() + ": " + throwable
                    : "watch " + watch + " on " + (onFirstListener ? "first listener" : "last listener gone") + ": " + throwable;
        }
    }

    private final List<Failure> failures = new CopyOnWriteArrayList<Failure>();

    @Override
    public void onSubscriberFailure(ECEventSubscription<?> subscription, IECEvent event, Throwable failure) {
        failures.add(new Failure(subscription, event, null, false, failure));
    }

    @Override
    public void onWatchFailure(ECListenerWatch watch, boolean onFirstListener, Throwable failure) {
        failures.add(new Failure(null, null, watch, onFirstListener, failure));
    }

    /** Every failure reported so far, in the order it arrived. */
    public List<Failure> getFailures() {
        return Collections.unmodifiableList(new ArrayList<Failure>(failures));
    }

    public int getFailureCount() {
        return failures.size();
    }

    /** Forgets everything recorded so far, so one test can watch two posts apart. */
    public void reset() {
        failures.clear();
    }

    @Override
    public String toString() {
        return "RecordingExceptionHandler" + failures;
    }

}
