package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventExceptionHandler;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;

/**
 * The exception handler that fails the test: a subscriber or a watch callback that throws becomes an
 * {@link AssertionError} at the post - or the refresh - that ran it, with the real failure as its
 * cause. Nothing is swallowed and nothing is merely logged.
 *
 * <p>{@code @ECoreTest} installs one on the global bus for the length of every test class, so a test
 * that only asserts on state still fails when a subscriber it drove broke. A test that breaks a
 * subscriber on purpose uses a scoped bus with a {@link RecordingExceptionHandler} instead.</p>
 */
public final class FailingExceptionHandler implements ECEventExceptionHandler {

    @Override
    public void onSubscriberFailure(ECEventSubscription<?> subscription, IECEvent event, Throwable failure) {
        throw new AssertionError("Subscriber " + subscription + " failed on " + event.getClass().getName(), failure);
    }

    @Override
    public void onWatchFailure(ECListenerWatch watch, boolean onFirstListener, Throwable failure) {
        throw new AssertionError("Listener watch " + watch + " failed on "
                + (onFirstListener ? "the first listener" : "the last listener gone"), failure);
    }

    @Override
    public String toString() {
        return "FailingExceptionHandler";
    }

}
