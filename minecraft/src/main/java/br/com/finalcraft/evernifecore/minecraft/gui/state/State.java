package br.com.finalcraft.evernifecore.minecraft.gui.state;

import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;

/**
 * A value a gui reads and re-reads. Whoever reads it can ask to be told when it goes stale, which is
 * how a component knows it has to render again.
 *
 * <p>Invalidation is not conditional on the value having changed - see
 * {@link MutableState#set(Object)}. Deduplication happens once, at the far end, on the rendered
 * output.</p>
 */
public interface State<T> {

    T get();

    /**
     * Registers a callback fired every time this state is invalidated. Cancel the returned handle to
     * stop listening; a view does it for every state it subscribed to when it closes.
     */
    Cancellable addListener(Runnable onInvalidate);

    /** A standalone state, shareable by any number of components. */
    static <T> MutableState<T> of(T initial) {
        return new MutableState<>(initial);
    }

}
