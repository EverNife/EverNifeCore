package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
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
     * As {@link #denied(ActionContext, Class, Function)}, with the {@code Pre} of the context's source taken from
     * {@link ActionEvents}; a source with no action event is only asked of the regions.
     */
    public static boolean denied(ActionContext context) {
        try (RegionDispatch dispatch = RegionLookup.get().dispatch()) {
            if (dispatch.decide(context) == Decision.DENY) {
                return true;
            }
            ActionEvents.Pair pair = ActionEvents.of(context.getSource());
            if (pair == null) {
                return false;
            }
            ActionEvent<?> posted = ECEventBus.global().postIfListened(anyEvent(pair.getPre()), () -> pair.newPre(context));
            return posted instanceof ECCancellable && ((ECCancellable) posted).isCancelled();
        }
    }

    /**
     * As {@link #post(Class, Supplier)}, with the {@code Post} of {@code source} taken from {@link ActionEvents}: {@code context}
     * runs only when somebody listens. Nothing for a source with no action event.
     */
    public static void post(EventSource source, Supplier<? extends ActionContext> context) {
        ActionEvents.Pair pair = ActionEvents.of(source);
        if (pair != null) {
            post(anyEvent(pair.getPost()), () -> pair.newPost(context.get()));
        }
    }

    @SuppressWarnings("unchecked")   //the catalog's classes are ActionEvents; the bus wants one exact type to answer for
    private static Class<ActionEvent<?>> anyEvent(Class<? extends ActionEvent<?>> type) {
        return (Class<ActionEvent<?>>) (Class<?>) type;
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
