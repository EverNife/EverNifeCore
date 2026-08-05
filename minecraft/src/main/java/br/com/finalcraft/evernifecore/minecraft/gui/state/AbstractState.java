package br.com.finalcraft.evernifecore.minecraft.gui.state;

import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The listener bookkeeping every {@link State} shares. A subclass decides where its value comes from
 * and calls {@link #invalidate()} when it goes stale.
 */
public abstract class AbstractState<T> implements State<T> {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Override
    public Cancellable addListener(Runnable onInvalidate) {
        if (onInvalidate == null) {
            return Cancellable.NONE;
        }
        listeners.add(onInvalidate);
        return () -> listeners.remove(onInvalidate);
    }

    /** Tells every listener this state is stale. A listener that throws does not stop the others. */
    protected void invalidate() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /** Whether anything is still reading this state. */
    protected boolean hasListeners() {
        return !listeners.isEmpty();
    }

}
