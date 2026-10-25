package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.region.RegionDispatch;
import br.com.finalcraft.evernifecore.region.RegionLookup;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What every platform listener does past its source gate, on the thread of the native event: the regions decide, the
 * {@code Pre} event is posted, and after the outcome the {@code Post} - each event built only when somebody listens.
 */
public final class Actions {

    private Actions() {
    }

    /**
     * The pre-outcome step: the regions decide, then {@code pre} is built and posted only if somebody listens. True when
     * the regions denied or a subscriber cancelled - the caller cancels the native event. The context's handles are
     * closed when it returns.
     */
    public static <C extends ActionContext, P extends ActionEvent<?> & ECCancellable> boolean denied(C context, Class<P> preType,
                                                                                                   Function<? super C, ? extends P> pre) {
        try (RegionDispatch dispatch = RegionLookup.get().dispatch()) {
            return denied(dispatch, context, preType, pre);
        }
    }

    /** As {@link #denied(ActionContext, Class, Function)}, one stage of an event asked in stages; the dispatch closes the context. */
    public static <C extends ActionContext, P extends ActionEvent<?> & ECCancellable> boolean denied(RegionDispatch dispatch, C context,
                                                                                                   Class<P> preType,
                                                                                                   Function<? super C, ? extends P> pre) {
        if (dispatch.decide(context) == Decision.DENY) {
            return true;
        }
        P posted = ECEventBus.global().postIfListened(preType, () -> pre.apply(context));
        return posted != null && posted.isCancelled();
    }

    /**
     * The pre-outcome step of an action the regions do not decide through a context: only {@code pre}, built and posted
     * only if somebody listens. True when a subscriber cancelled; the context's handles are closed after the post.
     */
    public static <P extends ActionEvent<?> & ECCancellable> boolean cancelled(Class<P> preType, Supplier<? extends P> pre) {
        P posted = ECEventBus.global().postIfListened(preType, pre);
        if (posted == null) {
            return false;
        }
        posted.getContext().close();
        return posted.isCancelled();
    }

    /** The post-outcome step: {@code post} built and posted only if somebody listens; its context's handles closed after. */
    public static <E extends ActionEvent<?>> void post(Class<E> postType, Supplier<? extends E> post) {
        E posted = ECEventBus.global().postIfListened(postType, post);
        if (posted != null) {
            posted.getContext().close();
        }
    }
}
