package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Delivers {@link IECEvent}s to whoever subscribed on this bus and, on the global bus only, mirrors
 * them into every registered {@link ECNativeAudience}.
 *
 * <p>A post runs the local phase first - every matching handler in priority order - and only then
 * the audiences, in registration order. A handler or an audience that throws is logged and skipped:
 * it never breaks the producer nor the ones queued behind it.</p>
 *
 * <p>What reaches an audience is decided by the event's own hierarchy: an {@link ECEvent} is
 * platform-visible, anything that merely implements {@link IECEvent} is not. {@link
 * #postLocal(IECEvent)} is the escape for the one post that must stay in.</p>
 *
 * <p>The bus also knows, per event class, whether anyone listens - here or on a native audience -
 * and it says so before an event exists: {@link #hasListeners(Class)} is the question, {@link
 * #postIfListened(Class, Supplier)} builds and posts only on a yes, and {@link
 * #watchListeners(Class, Runnable, Runnable)} runs an action when the first listener of a type
 * arrives and another when the last one leaves - what lets a producer keep an expensive source
 * switched off until somebody cares.</p>
 *
 * <p>{@link #global()} is the bus platforms mirror from and plugins subscribe to; it lives as long
 * as the classloader does. {@link #create()} builds a scoped bus for a subsystem's own traffic - it
 * never mirrors, so an audience added to it is accepted and never called, and its watches only ever
 * see the local subscribers.</p>
 */
public class ECEventBus {

    private static final ECEventBus GLOBAL = new ECEventBus(true);

    /** The process-wide bus: the only one that mirrors into the native audiences. */
    public static ECEventBus global() {
        return GLOBAL;
    }

    /** A bus of its own, for traffic that must not reach any platform: it never mirrors. */
    public static ECEventBus create() {
        return new ECEventBus(false);
    }

    private final boolean mirroring;

    private final List<ECNativeAudience> audiences = new CopyOnWriteArrayList<>();

    private final Object mutex = new Object();
    private final List<Handler> handlers = new ArrayList<>();               // guarded by mutex
    //Dispatch plan per concrete event class, priority-sorted, built lazily and dropped wholesale on
    //every (un)subscribe. Reading it is lock-free, and the array a post iterates is a snapshot - a
    //handler may subscribe or post while it runs.
    private volatile Map<Class<?>, Handler[]> dispatchIndex = Collections.emptyMap();

    private final List<Watch> watches = new CopyOnWriteArrayList<>();
    //One evaluation pass at a time: a refresh that lands while a pass runs only marks it dirty and
    //returns, and the running pass re-reads everything after its callbacks. Callbacks never run under
    //a lock, so one that subscribes, unsubscribes or refreshes is safe.
    private final Object watchLock = new Object();
    private boolean evaluatingWatches;                                       // guarded by watchLock
    private boolean watchesDirty;                                            // guarded by watchLock

    //Package-private: production only ever gets the global bus or a scoped one, and a test needs a
    //mirroring bus that is not the global.
    ECEventBus(boolean mirroring) {
        this.mirroring = mirroring;
    }

    // ------------------------------------------------------------------
    //  Subscribing
    // ------------------------------------------------------------------

    /** Subscribes {@code handler} to {@code eventType} and every subtype of it, at NORMAL priority. */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(Class<T> eventType, Consumer<? super T> handler) {
        return subscribe(null, eventType, ECEventPriority.NORMAL, handler);
    }

    /** As {@link #subscribe(Class, Consumer)}, at the given priority. */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(Class<T> eventType, ECEventPriority priority, Consumer<? super T> handler) {
        return subscribe(null, eventType, priority, handler);
    }

    /**
     * As {@link #subscribe(Class, Consumer)}, owned by {@code plugin}: the subscription is dropped
     * with the rest of that plugin's when it shuts down ({@link #unsubscribeAll(ECPluginData)}).
     */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(ECPluginData plugin, Class<T> eventType, Consumer<? super T> handler) {
        return subscribe(plugin, eventType, ECEventPriority.NORMAL, handler);
    }

    /** As {@link #subscribe(ECPluginData, Class, Consumer)}, at the given priority. */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(@Nullable ECPluginData plugin, Class<T> eventType, ECEventPriority priority, Consumer<? super T> handler) {
        Objects.requireNonNull(eventType, "'eventType' cannot be null when subscribing to an ECEventBus!");
        Objects.requireNonNull(priority, "'priority' cannot be null when subscribing to an ECEventBus!");
        Objects.requireNonNull(handler, "'handler' cannot be null when subscribing to an ECEventBus!");

        Subscription<T> subscription = new Subscription<>(eventType);
        subscription.handler = new Handler(
                eventType,
                subscription,
                plugin == null ? null : plugin.getMetaInfo().getName(),
                priority.getValue(),
                false,
                event -> handler.accept(eventType.cast(event))
        );

        addHandlers(Collections.singletonList(subscription.handler));
        return subscription;
    }

    /**
     * Subscribes every {@link ECEventHandler}-annotated method of {@code listener} whose single
     * parameter is an {@link IECEvent}. Any other signature belongs to a platform's own listener
     * registration and is left alone here.
     */
    public void register(Object listener) {
        if (listener == null) return;

        List<MethodInvoker<?>> annotatedMethods = FCReflectionUtil.getMethods()
                .getMethods(listener.getClass(), method -> method.getAnnotation(ECEventHandler.class) != null)
                .collect(Collectors.toList());

        List<Handler> toAdd = new ArrayList<>();
        for (MethodInvoker<?> invoker : annotatedMethods) {
            Method method = invoker.getMethod();
            Class<?>[] parameterTypes = method.getParameterTypes();

            if (parameterTypes.length != 1 || !IECEvent.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }

            ECEventHandler annotation = method.getAnnotation(ECEventHandler.class);
            short priority = ECEventPriority.of(annotation);

            toAdd.add(new Handler(parameterTypes[0], listener, null, priority, annotation.ignoreCancelled(),
                    event -> invoker.invoke(listener, event)));
        }

        if (toAdd.isEmpty()) return;
        addHandlers(toAdd);
    }

    /** Drops every handler {@link #register(Object)} took from this listener. */
    public void unregister(Object listener) {
        if (listener == null) return;
        removeHandlers(handler -> handler.owner == listener);
    }

    /**
     * Drops every subscription opened in this plugin's name and stops every listener watch it owns -
     * the one shutdown drain. The subscriptions go first, so a watch closed by their departure still
     * hears it - the plugin's own included - and only then its watches detach; a watch that is still
     * open at that point detaches silently, like {@link ECListenerWatch#stop()} always does.
     */
    public void unsubscribeAll(ECPluginData plugin) {
        Objects.requireNonNull(plugin, "'plugin' cannot be null when draining its ECEventBus subscriptions!");
        String pluginName = plugin.getMetaInfo().getName();
        removeHandlers(handler -> pluginName.equals(handler.pluginName));
        for (Watch watch : watches) {
            if (pluginName.equals(watch.pluginName)) {
                watch.stop();
            }
        }
    }

    // ------------------------------------------------------------------
    //  Posting
    // ------------------------------------------------------------------

    /**
     * Delivers {@code event} locally and then to the native audiences, and returns it - so a producer
     * can read back whatever the handlers changed on it.
     */
    public <T extends IECEvent> T post(T event) {
        if (event == null) return null;

        deliverLocal(event);

        //The hierarchy IS the mirror policy: only an ECEvent is platform-visible, so an event that
        //just implements IECEvent stays inside this bus by construction.
        if (mirroring && event instanceof ECEvent) {
            for (ECNativeAudience audience : audiences) {
                try {
                    if (audience.hasListeners(event.getClass())) {
                        audience.dispatch(event);
                    }
                } catch (Throwable t) {
                    //The SPI says an audience swallows its own errors; this is the belt to that suspender.
                    EverNifeCore.getLog().severe("[ECEventBus] Native audience '{}' failed on {}; the"
                            + " remaining audiences still run.", audience.name(), event.getClass().getName(), t);
                }
            }
        }

        return event;
    }

    /**
     * Whether a post of exactly {@code eventType} would reach anyone: a handler subscribed to that type,
     * to a supertype of it or to a marker interface it carries - or, on a mirroring bus and for an
     * {@link ECEvent}, a native audience with listeners. A handler subscribed to a subtype does not
     * count: it would not hear that post. Cheap - one index lookup and one question per audience - so
     * a producer may ask it on every occasion before building anything.
     */
    public boolean hasListeners(Class<? extends IECEvent> eventType) {
        Objects.requireNonNull(eventType, "'eventType' cannot be null when asking an ECEventBus for listeners!");

        if (planFor(eventType).length > 0) {
            return true;
        }
        if (mirroring && ECEvent.class.isAssignableFrom(eventType)) {
            for (ECNativeAudience audience : audiences) {
                try {
                    if (audience.hasListeners(eventType)) {
                        return true;
                    }
                } catch (Throwable t) {
                    EverNifeCore.getLog().severe("[ECEventBus] Native audience '{}' failed to answer whether {}"
                            + " has listeners; counted as none.", audience.name(), eventType.getName(), t);
                }
            }
        }
        return false;
    }

    /**
     * Builds and posts the event only when {@link #hasListeners(Class)} says yes. Returns the posted
     * event, or {@code null} when nobody listened - then {@code event} never ran, which is the point:
     * an event that costs something to build is never built for nobody.
     */
    @Nullable
    public <T extends IECEvent> T postIfListened(Class<T> eventType, Supplier<? extends T> event) {
        Objects.requireNonNull(event, "'event' cannot be null when posting to an ECEventBus!");
        if (!hasListeners(eventType)) {
            return null;
        }
        return post(event.get());
    }

    /** Delivers {@code event} to this bus only: no audience is consulted, whatever the event is. */
    public <T extends IECEvent> T postLocal(T event) {
        if (event == null) return null;
        deliverLocal(event);
        return event;
    }

    private void deliverLocal(IECEvent event) {
        for (Handler handler : planFor(event.getClass())) {
            if (handler.ignoreCancelled && event instanceof ECCancellable && ((ECCancellable) event).isCancelled()) {
                continue;
            }
            try {
                handler.action.accept(event);
            } catch (Throwable t) {
                EverNifeCore.getLog().severe("[ECEventBus] Handler {} failed on {}; the remaining handlers"
                        + " still run.", handler.describe(), event.getClass().getName(), t);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Watching who listens
    // ------------------------------------------------------------------

    /**
     * Follows the presence of listeners for {@code eventType} - the same presence {@link
     * #hasListeners(Class)} reports: {@code onFirstListener} runs when the first one arrives (right
     * away, if one is already there when the watch is taken), {@code onLastListenerGone} when the last
     * one leaves. Exactly one callback per transition, on the thread that caused it and outside every
     * lock of this bus; a callback that throws is logged, and the watch keeps the state it had just
     * moved to. What a producer does with it is switch its expensive source on and off: register a
     * native listener, start a task, warm a cache.
     */
    public ECListenerWatch watchListeners(Class<? extends IECEvent> eventType, Runnable onFirstListener, @Nullable Runnable onLastListenerGone) {
        return watchListeners(null, eventType, onFirstListener, onLastListenerGone);
    }

    /**
     * As {@link #watchListeners(Class, Runnable, Runnable)}, present while ANY of {@code eventTypes}
     * has listeners - for a producer of a whole family, which asks about the concrete types it posts.
     */
    public ECListenerWatch watchListeners(Collection<? extends Class<? extends IECEvent>> eventTypes, Runnable onFirstListener, @Nullable Runnable onLastListenerGone) {
        return watchListeners(null, eventTypes, onFirstListener, onLastListenerGone);
    }

    /**
     * As {@link #watchListeners(Class, Runnable, Runnable)}, owned by {@code plugin}: the watch is
     * stopped with the rest of that plugin's when it shuts down ({@link #unsubscribeAll(ECPluginData)}).
     */
    public ECListenerWatch watchListeners(@Nullable ECPluginData plugin, Class<? extends IECEvent> eventType, Runnable onFirstListener, @Nullable Runnable onLastListenerGone) {
        Objects.requireNonNull(eventType, "'eventType' cannot be null when watching listeners on an ECEventBus!");
        return watchListeners(plugin, Collections.<Class<? extends IECEvent>>singletonList(eventType), onFirstListener, onLastListenerGone);
    }

    /** As {@link #watchListeners(Collection, Runnable, Runnable)}, owned by {@code plugin}. */
    public ECListenerWatch watchListeners(@Nullable ECPluginData plugin, Collection<? extends Class<? extends IECEvent>> eventTypes, Runnable onFirstListener, @Nullable Runnable onLastListenerGone) {
        Objects.requireNonNull(eventTypes, "'eventTypes' cannot be null when watching listeners on an ECEventBus!");
        Objects.requireNonNull(onFirstListener, "'onFirstListener' cannot be null when watching listeners on an ECEventBus!");
        if (eventTypes.isEmpty()) {
            throw new IllegalArgumentException("A listener watch needs at least one event type to follow - "
                    + "an empty collection would be a watch that never fires.");
        }
        for (Class<? extends IECEvent> eventType : eventTypes) {
            Objects.requireNonNull(eventType, "'eventTypes' cannot hold a null when watching listeners on an ECEventBus!");
        }

        Watch watch = new Watch(
                Collections.unmodifiableList(new ArrayList<Class<? extends IECEvent>>(eventTypes)),
                plugin == null ? null : plugin.getMetaInfo().getName(),
                onFirstListener,
                onLastListenerGone
        );
        watches.add(watch);
        //born absent: if somebody already listens, this first pass is what fires onFirstListener
        refreshListenerWatches();
        return watch;
    }

    /**
     * Re-evaluates every watch and fires the callbacks of those whose presence changed. The bus calls
     * it on its own mutations; a native audience calls it when its own listeners for an EC event
     * appeared or vanished, since the bus cannot see a native registration by itself. Idempotent:
     * calling it for nothing costs one pass and fires nothing.
     */
    public void refreshListenerWatches() {
        synchronized (watchLock) {
            if (evaluatingWatches) {
                watchesDirty = true;
                return;
            }
            evaluatingWatches = true;
        }

        boolean finished = false;
        try {
            while (true) {
                synchronized (watchLock) {
                    watchesDirty = false;
                }
                for (Watch watch : watches) {
                    if (!watch.active) continue;
                    boolean now = false;
                    for (Class<? extends IECEvent> eventType : watch.eventTypes) {
                        if (hasListeners(eventType)) {
                            now = true;
                            break;
                        }
                    }
                    if (now == watch.present) continue;
                    watch.present = now;                    //state first, so a re-entrant read sees the truth
                    Runnable action = now ? watch.onFirstListener : watch.onLastListenerGone;
                    if (action == null) continue;
                    try {
                        action.run();
                    } catch (Throwable t) {
                        EverNifeCore.getLog().severe("[ECEventBus] Listener watch on {} failed on {}; its state"
                                + " still moved to {}.", watch.describe(), now ? "the first listener" : "the last listener gone",
                                now ? "present" : "absent", t);
                    }
                }
                //"nothing new arrived" and "this pass is over" are decided under one lock, so a refresh
                //that lands in between is either seen by this pass or starts the next - never dropped
                synchronized (watchLock) {
                    if (!watchesDirty) {
                        evaluatingWatches = false;
                        finished = true;
                        return;
                    }
                }
            }
        } finally {
            //a pass that dies unexpectedly must not leave the bus believing one is still running, or no
            //watch would ever fire again; the normal exit already cleared the flag above
            if (!finished) {
                synchronized (watchLock) {
                    evaluatingWatches = false;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Native audiences
    // ------------------------------------------------------------------

    /**
     * Adds {@code audience}, or replaces the one already answering to the same {@link
     * ECNativeAudience#name()} - which is what makes registering it on every plugin enable safe.
     */
    public void addNativeAudience(ECNativeAudience audience) {
        Objects.requireNonNull(audience, "'audience' cannot be null when adding it to an ECEventBus!");
        String name = Objects.requireNonNull(audience.name(), "An ECNativeAudience must answer a stable name(): "
                + "it is the key that keeps a re-registration from adding a second copy of the same audience.");

        synchronized (mutex) {
            boolean replaced = false;
            for (int index = 0; index < audiences.size(); index++) {
                if (name.equals(audiences.get(index).name())) {
                    //Replacing in place keeps the delivery order stable across a re-enable, instead of
                    //shuffling the audience to the back of the queue.
                    audiences.set(index, audience);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                audiences.add(audience);
            }
        }
        refreshListenerWatches();
    }

    /** Removes the audience answering to {@code name}, if any. */
    public void removeNativeAudience(String name) {
        if (name == null) return;
        boolean removed;
        synchronized (mutex) {
            removed = audiences.removeIf(audience -> name.equals(audience.name()));
        }
        if (removed) {
            refreshListenerWatches();
        }
    }

    /** The audiences currently mirrored into, in delivery order. */
    public List<ECNativeAudience> getNativeAudiences() {
        return Collections.unmodifiableList(new ArrayList<>(audiences));
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    private void addHandlers(List<Handler> toAdd) {
        synchronized (mutex) {
            handlers.addAll(toAdd);
            dispatchIndex = Collections.emptyMap();
        }
        refreshListenerWatches();
    }

    private void removeHandlers(Predicate<Handler> filter) {
        boolean removedAny = false;
        synchronized (mutex) {
            for (Iterator<Handler> iterator = handlers.iterator(); iterator.hasNext(); ) {
                Handler handler = iterator.next();
                if (filter.test(handler)) {
                    iterator.remove();
                    handler.active = false;
                    removedAny = true;
                }
            }
            if (removedAny) {
                dispatchIndex = Collections.emptyMap();
            }
        }
        if (removedAny) {
            refreshListenerWatches();
        }
    }

    private Handler[] planFor(Class<?> eventClass) {
        Handler[] plan = dispatchIndex.get(eventClass);
        if (plan != null) {
            return plan;
        }

        synchronized (mutex) {
            plan = dispatchIndex.get(eventClass);
            if (plan != null) {
                return plan;
            }

            List<Handler> matching = new ArrayList<>();
            for (Handler handler : handlers) {
                //isAssignableFrom covers superclasses AND interfaces, so subscribing to a marker
                //interface keeps hearing every event that carries it.
                if (handler.eventType.isAssignableFrom(eventClass)) {
                    matching.add(handler);
                }
            }
            //Stable sort over a subscription-ordered list: ties keep the order they subscribed in.
            matching.sort(Comparator.comparingInt(handler -> handler.priority));
            plan = matching.toArray(new Handler[0]);

            Map<Class<?>, Handler[]> updated = new HashMap<>(dispatchIndex);
            updated.put(eventClass, plan);
            dispatchIndex = updated;
            return plan;
        }
    }

    private static final class Handler {
        final Class<?> eventType;
        final Object owner;                 // the listener, or the Subscription that opened it
        final String pluginName;            // null when the subscription is not owned by a plugin
        final short priority;
        final boolean ignoreCancelled;
        final Consumer<IECEvent> action;
        volatile boolean active = true;

        Handler(Class<?> eventType, Object owner, String pluginName, short priority, boolean ignoreCancelled, Consumer<IECEvent> action) {
            this.eventType = eventType;
            this.owner = owner;
            this.pluginName = pluginName;
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
            this.action = action;
        }

        String describe() {
            return owner.getClass().getName() + " on " + eventType.getName();
        }
    }

    private final class Subscription<T extends IECEvent> implements ECEventSubscription<T> {
        private final Class<T> eventType;
        //Assigned right after construction and before the handler reaches the bus; volatile so a
        //subscriber that hands the handle to another thread cannot read it half-published.
        private volatile Handler handler;

        Subscription(Class<T> eventType) {
            this.eventType = eventType;
        }

        @Override
        public Class<T> getEventType() {
            return eventType;
        }

        @Override
        public void unsubscribe() {
            removeHandlers(candidate -> candidate == handler);
        }

        @Override
        public boolean isActive() {
            return handler.active;
        }
    }

    private final class Watch implements ECListenerWatch {
        final List<Class<? extends IECEvent>> eventTypes;
        final String pluginName;            // null when the watch is not owned by a plugin
        final Runnable onFirstListener;
        final Runnable onLastListenerGone;  // null when the producer has nothing to undo
        //Both only ever written by the single evaluation pass; volatile so a read from any thread
        //sees what the last callback was told.
        volatile boolean present;
        volatile boolean active = true;

        Watch(List<Class<? extends IECEvent>> eventTypes, String pluginName, Runnable onFirstListener, Runnable onLastListenerGone) {
            this.eventTypes = eventTypes;
            this.pluginName = pluginName;
            this.onFirstListener = onFirstListener;
            this.onLastListenerGone = onLastListenerGone;
        }

        @Override
        public Collection<Class<? extends IECEvent>> getEventTypes() {
            return eventTypes;
        }

        @Override
        public boolean hasListeners() {
            return present;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void stop() {
            active = false;
            watches.remove(this);
        }

        String describe() {
            if (eventTypes.size() == 1) {
                return eventTypes.get(0).getName();
            }
            return eventTypes.stream().map(Class::getSimpleName).collect(Collectors.joining(", ", "[", "]"));
        }
    }

}
