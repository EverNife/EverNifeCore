package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

/**
 * What an {@link ECEventBus} does with a failure in the code it calls on behalf of somebody else - a
 * subscriber, a listener-watch callback. The bus never lets such a failure reach the producer nor stop
 * the ones queued behind it; the handler decides what it becomes instead: a log line, a metric, a
 * failed test. One abstract method, so a lambda is a handler.
 *
 * <p>The bus does <b>not</b> guard the handler itself: whatever it throws propagates to the producer.
 * That is the point of a test handler that throws - the test fails where the post was made.</p>
 */
public interface ECEventExceptionHandler {

    /**
     * {@code subscription} threw {@code failure} while handling {@code event}. The bus already moved on
     * to the next subscriber.
     */
    void onSubscriberFailure(ECEventSubscription<?> subscription, IECEvent event, Throwable failure);

    /**
     * A callback of {@code watch} threw - {@code onFirstListener} says which side - and the watch keeps
     * the state it had just moved to. Defaults to what {@link #LOGGING} does.
     */
    default void onWatchFailure(ECListenerWatch watch, boolean onFirstListener, Throwable failure) {
        LOGGING.onWatchFailure(watch, onFirstListener, failure);
    }

    /**
     * The production default: SEVERE on the core log, the subscription or watch named and the stack
     * attached, and never a throw of its own.
     */
    ECEventExceptionHandler LOGGING = new ECEventExceptionHandler() {

        @Override
        public void onSubscriberFailure(ECEventSubscription<?> subscription, IECEvent event, Throwable failure) {
            EverNifeCore.getLog().severe("[ECEventBus] Subscriber {} failed on {}; the remaining subscribers still run.",
                    subscription, event.getClass().getName(), failure);
        }

        @Override
        public void onWatchFailure(ECListenerWatch watch, boolean onFirstListener, Throwable failure) {
            EverNifeCore.getLog().severe("[ECEventBus] Listener watch {} failed on {}; its state still moved to {}.",
                    watch, onFirstListener ? "the first listener" : "the last listener gone",
                    onFirstListener ? "present" : "absent", failure);
        }

        @Override
        public String toString() {
            return "ECEventExceptionHandler.LOGGING";
        }
    };

}
