package br.com.finalcraft.evernifecore.minecraft.gui.state;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The listener bookkeeping every {@link State} shares. A subclass decides where its value comes from
 * and calls {@link #invalidate()} when it goes stale.
 */
public abstract class AbstractState<T> implements State<T> {

    private final List<Subscription> listeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribes {@code onInvalidate} and answers the handle that drops that subscription.
     *
     * <p>The same {@code Runnable} may be subscribed any number of times, and each handle cancels
     * only the subscription it created: two owners sharing one callback do not cancel each other,
     * and cancelling twice is a no-op.</p>
     */
    @Override
    public Cancellable addListener(Runnable onInvalidate) {
        if (onInvalidate == null) {
            return Cancellable.NONE;
        }
        Subscription subscription = new Subscription(onInvalidate);
        listeners.add(subscription);
        AtomicBoolean cancelled = new AtomicBoolean();
        return () -> {
            if (cancelled.compareAndSet(false, true)) {
                listeners.remove(subscription);
            }
        };
    }

    /** Tells every listener this state is stale. A listener that throws does not stop the others. */
    protected void invalidate() {
        for (Subscription subscription : listeners) {
            try {
                subscription.onInvalidate.run();
            } catch (Throwable e) {
                //the state's own toString may be the caller's object, and a second failure here would
                //silence the listeners this loop still has to reach
                EverNifeCore.getLog().severe("A listener of a gui " + getClass().getSimpleName()
                        + " failed while it was being told the value went stale", e);
            }
        }
    }

    /** One subscription, told apart from the next by identity even when both wrap the same callback. */
    private static final class Subscription {

        private final Runnable onInvalidate;

        private Subscription(Runnable onInvalidate) {
            this.onInvalidate = onInvalidate;
        }

    }

}
