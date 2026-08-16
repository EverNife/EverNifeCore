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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Delivers {@link IECEvent}s to whoever subscribed on this bus and, on the global bus only, mirrors
 * them into every registered {@link ECNativeAudience} - and knows, per event class and before any
 * event exists, whether anyone at all is there to hear it.
 *
 * <p>A post runs the local phase first - every matching subscription in priority order - and only
 * then the audiences, in registration order. A subscriber that throws is handed to the bus's
 * {@link ECEventExceptionHandler} and the ones queued behind it still run; an audience that throws is
 * logged and skipped. Neither ever breaks the producer. What reaches an audience is decided by the
 * event's own hierarchy: an {@link ECEvent} is platform-visible, anything that merely implements
 * {@link IECEvent} is not. {@link #postLocal(IECEvent)} is the escape for the one post that must
 * stay in.</p>
 *
 * <p>A subscription is an {@link ECEventSubscription}: the handle {@link #subscribe} and
 * {@link #register} hand back, and the view {@link #getSubscriptions()} lists - who listens to what,
 * from which plugin, with which {@link ECSubscribeOptions}. {@link #unsubscribeIf(Predicate)} takes
 * any of them away by any criterion.</p>
 *
 * <p>The presence of listeners is a question a producer asks up front: {@link #hasListeners(Class)}
 * answers it, {@link #postIfListened(Class, Supplier)} builds and posts only on a yes, and {@link
 * #watchListeners(Runnable, Runnable, Class[])} turns it into two actions - one when the first
 * listener of a type arrives, one when the last leaves - so an expensive source stays switched off
 * until somebody cares.</p>
 *
 * <p>{@link #global()} is the bus platforms mirror from and plugins subscribe to; it lives as long
 * as the classloader does and always reports failures through {@link ECEventExceptionHandler#LOGGING}.
 * {@link #create()} builds a scoped bus for a subsystem's own traffic - it never mirrors, so an
 * audience added to it is accepted and never called, and its presence is that of its own subscribers
 * alone; {@link #create(ECEventExceptionHandler)} gives it a failure policy of its own.</p>
 */
public class ECEventBus {

    private static final ECEventBus GLOBAL = new ECEventBus(true, ECEventExceptionHandler.LOGGING);

    /** The process-wide bus: the only one that mirrors into the native audiences. */
    public static ECEventBus global() {
        return GLOBAL;
    }

    /** A bus of its own, for traffic that must not reach any platform: it never mirrors. Failures are logged. */
    public static ECEventBus create() {
        return new ECEventBus(false, ECEventExceptionHandler.LOGGING);
    }

    /** As {@link #create()}, with {@code exceptionHandler} deciding what a failing subscriber or watch callback becomes. */
    public static ECEventBus create(ECEventExceptionHandler exceptionHandler) {
        Objects.requireNonNull(exceptionHandler, "'exceptionHandler' cannot be null when creating an ECEventBus: use create() for the logging default.");
        return new ECEventBus(false, exceptionHandler);
    }

    private final boolean mirroring;
    private volatile ECEventExceptionHandler exceptionHandler;

    private final List<ECNativeAudience> audiences = new CopyOnWriteArrayList<>();

    private final Object mutex = new Object();
    //Every live subscription under the class it NAMED, each list in subscription order. The plan of a
    //concrete class is assembled from the lists of its ancestors, so a subscribe touches one list and
    //a post never scans them all.
    private final Map<Class<?>, List<Subscription<?>>> subscriptionsByType = new HashMap<>();   // guarded by mutex
    private long nextSequence;                                                                    // guarded by mutex
    //Dispatch plan per concrete event class, priority-sorted, built lazily and dropped wholesale on
    //every (un)subscribe. Reading it is lock-free, and the array a post iterates is a snapshot - a
    //subscriber may subscribe or post while it runs.
    private volatile Map<Class<?>, Subscription<?>[]> dispatchIndex = Collections.emptyMap();

    private final List<Watch> watches = new CopyOnWriteArrayList<>();
    //One evaluation pass at a time: a refresh that lands while a pass runs only marks it dirty and
    //returns, and the running pass re-reads everything after its callbacks. Callbacks never run under
    //a lock, so one that subscribes, unsubscribes or refreshes is safe.
    private final Object watchLock = new Object();
    private boolean evaluatingWatches;                                       // guarded by watchLock
    private boolean watchesDirty;                                            // guarded by watchLock

    //Package-private: production only ever gets the global bus or a scoped one, and a test needs a
    //mirroring bus that is not the global.
    ECEventBus(boolean mirroring, ECEventExceptionHandler exceptionHandler) {
        this.mirroring = mirroring;
        this.exceptionHandler = exceptionHandler;
    }

    /** The policy a failing subscriber or watch callback goes through on this bus. */
    public ECEventExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Not API. Package-private so the knob stays out of what a plugin can reach by accident: production
     * never re-routes the failures of another plugin's subscribers, a test engine does - it swaps the
     * global bus's handler for the length of a test class and puts the previous one back. A fence,
     * not a lock: on the Java 8 floor any class declared in this package gets here.
     */
    void setExceptionHandler(ECEventExceptionHandler exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "'exceptionHandler' cannot be null on an ECEventBus: hand it ECEventExceptionHandler.LOGGING to go back to the default.");
    }

    // ------------------------------------------------------------------
    //  Subscribing
    // ------------------------------------------------------------------

    /** Subscribes to {@code eventType} and every subtype of it with {@link ECSubscribeOptions#defaults()}. */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(Class<T> eventType, ECEventSubscriber<? super T> subscriber) {
        return subscribe(eventType, ECSubscribeOptions.defaults(), subscriber);
    }

    /**
     * As {@link #subscribe(Class, ECEventSubscriber)}, owned by {@code plugin}: the subscription is
     * dropped with the rest of that plugin's when it shuts down ({@link #unsubscribeAll(ECPluginData)}).
     */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(ECPluginData plugin, Class<T> eventType, ECEventSubscriber<? super T> subscriber) {
        return subscribe(eventType, ECSubscribeOptions.ownedBy(plugin), subscriber);
    }

    /** Subscribes to {@code eventType} the way {@code options} says: priority, cancellation, exactness, owner. */
    public <T extends IECEvent> ECEventSubscription<T> subscribe(Class<T> eventType, ECSubscribeOptions options, ECEventSubscriber<? super T> subscriber) {
        Objects.requireNonNull(eventType, "'eventType' cannot be null when subscribing to an ECEventBus!");
        Objects.requireNonNull(options, "'options' cannot be null when subscribing to an ECEventBus: use ECSubscribeOptions.defaults().");
        Objects.requireNonNull(subscriber, "'subscriber' cannot be null when subscribing to an ECEventBus!");

        Subscription<T> subscription = new Subscription<>(eventType, options, subscriber, null,
                event -> subscriber.handle(eventType.cast(event)));
        addSubscriptions(Collections.<Subscription<?>>singletonList(subscription));
        return subscription;
    }

    /**
     * Subscribes every {@link ECEventHandler}-annotated method of {@code listener} whose single
     * parameter is an {@link IECEvent}, owned by nobody. Any other signature belongs to a platform's
     * own listener registration and is left alone here.
     *
     * @return one subscription per method taken, in declaration order; empty when none was
     */
    public List<ECEventSubscription<?>> register(Object listener) {
        return register(null, listener);
    }

    /** As {@link #register(Object)}, with every subscription owned by {@code plugin}. */
    public List<ECEventSubscription<?>> register(@Nullable ECPluginData plugin, Object listener) {
        if (listener == null) return Collections.emptyList();

        List<MethodInvoker<?>> annotatedMethods = FCReflectionUtil.getMethods()
                .getMethods(listener.getClass(), method -> method.getAnnotation(ECEventHandler.class) != null)
                .collect(Collectors.toList());

        List<Subscription<?>> toAdd = new ArrayList<>();
        for (MethodInvoker<?> invoker : annotatedMethods) {
            Method method = invoker.getMethod();
            Class<?>[] parameterTypes = method.getParameterTypes();

            if (parameterTypes.length != 1 || !IECEvent.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }

            ECSubscribeOptions options = ECSubscribeOptions.of(method.getAnnotation(ECEventHandler.class), plugin);
            toAdd.add(annotatedSubscription(parameterTypes[0].asSubclass(IECEvent.class), options, listener, invoker));
        }

        if (toAdd.isEmpty()) return Collections.emptyList();
        addSubscriptions(toAdd);
        return Collections.unmodifiableList(new ArrayList<ECEventSubscription<?>>(toAdd));
    }

    private <T extends IECEvent> Subscription<T> annotatedSubscription(Class<T> eventType, ECSubscribeOptions options, Object listener, MethodInvoker<?> invoker) {
        return new Subscription<>(eventType, options, listener, invoker.getMethod(), event -> invoker.invoke(listener, event));
    }

    /** Drops every subscription {@link #register} took from this listener. */
    public void unregister(Object listener) {
        if (listener == null) return;
        unsubscribeIf(subscription -> subscription.getSubscriber() == listener);
    }

    /**
     * Drops every subscription {@code filter} accepts - by plugin, by type, by priority, by subscriber,
     * whatever the predicate reads off the view - and hands them back, in subscription order.
     * A listener watch whose last listener left this way hears it before this returns.
     */
    public List<ECEventSubscription<?>> unsubscribeIf(Predicate<? super ECEventSubscription<?>> filter) {
        Objects.requireNonNull(filter, "'filter' cannot be null when unsubscribing from an ECEventBus!");
        List<Subscription<?>> removed = new ArrayList<>();
        synchronized (mutex) {
            for (Iterator<List<Subscription<?>>> lists = subscriptionsByType.values().iterator(); lists.hasNext(); ) {
                List<Subscription<?>> declared = lists.next();
                for (Iterator<Subscription<?>> iterator = declared.iterator(); iterator.hasNext(); ) {
                    Subscription<?> subscription = iterator.next();
                    if (filter.test(subscription)) {
                        iterator.remove();
                        subscription.active = false;
                        removed.add(subscription);
                    }
                }
                if (declared.isEmpty()) {
                    lists.remove();
                }
            }
            if (!removed.isEmpty()) {
                dispatchIndex = Collections.emptyMap();
            }
        }
        if (!removed.isEmpty()) {
            refreshListenerWatches();
        }
        //the walk went type by type; the caller is promised subscription order
        removed.sort(BY_SEQUENCE);
        return Collections.unmodifiableList(new ArrayList<ECEventSubscription<?>>(removed));
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
        unsubscribeIf(subscription -> pluginName.equals(subscription.getOptions().getPluginName()));
        for (Watch watch : watches) {
            if (pluginName.equals(watch.pluginName)) {
                watch.stop();
            }
        }
    }

    // ------------------------------------------------------------------
    //  Introspection
    // ------------------------------------------------------------------

    /** Every live subscription on this bus, in the order they were taken. A snapshot. */
    public List<ECEventSubscription<?>> getSubscriptions() {
        List<Subscription<?>> all = new ArrayList<>();
        synchronized (mutex) {
            for (List<Subscription<?>> declared : subscriptionsByType.values()) {
                all.addAll(declared);
            }
        }
        all.sort(BY_SEQUENCE);
        return Collections.unmodifiableList(new ArrayList<ECEventSubscription<?>>(all));
    }

    /**
     * What a post of exactly {@code eventType} would reach on this bus, in delivery order - the same
     * set {@link #hasListeners(Class)} counts and for the same reason: a subscription to a subtype is
     * not in it. The audiences are not asked; this is the local phase only. A snapshot.
     */
    public List<ECEventSubscription<?>> getSubscriptions(Class<? extends IECEvent> eventType) {
        Objects.requireNonNull(eventType, "'eventType' cannot be null when asking an ECEventBus for subscriptions!");
        return Collections.unmodifiableList(new ArrayList<ECEventSubscription<?>>(Arrays.asList(planFor(eventType))));
    }

    /** Every active listener watch on this bus, in the order they were taken. A snapshot. */
    public List<ECListenerWatch> getListenerWatches() {
        return Collections.unmodifiableList(new ArrayList<ECListenerWatch>(watches));
    }

    // ------------------------------------------------------------------
    //  Posting
    // ------------------------------------------------------------------

    /**
     * Delivers {@code event} locally and then to the native audiences, and returns it - so a producer
     * can read back whatever the subscribers changed on it.
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
     * Whether a post of exactly {@code eventType} would reach anyone: a subscription to that type, to a
     * supertype of it or to a marker interface it carries (an {@code exact} one only to that very type) -
     * or, on a mirroring bus and for an {@link ECEvent}, a native audience with listeners. A subscription
     * to a subtype does not count: it would not hear that post. Cheap - one index lookup and one
     * question per audience - so a producer may ask it on every occasion before building anything.
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
        for (Subscription<?> subscription : planFor(event.getClass())) {
            if (subscription.options.isIgnoringCancelled() && event instanceof ECCancellable && ((ECCancellable) event).isCancelled()) {
                continue;
            }
            Throwable failure;
            try {
                subscription.action.handle(event);
                continue;
            } catch (Throwable t) {
                failure = t;
            }
            //outside the try on purpose: what the exception handler throws belongs to the producer
            exceptionHandler.onSubscriberFailure(subscription, event, failure);
        }
    }

    // ------------------------------------------------------------------
    //  Watching who listens
    // ------------------------------------------------------------------

    /**
     * Follows the presence of listeners for {@code eventTypes} - the same presence {@link
     * #hasListeners(Class)} reports, for ANY of them: {@code onFirstListener} runs when the first one
     * arrives (right away, if one is already there when the watch is taken), {@code onLastListenerGone}
     * when the last one leaves. Exactly one callback per transition, on the thread that caused it and
     * outside every lock of this bus; a callback that throws goes to the {@link ECEventExceptionHandler}
     * and the watch keeps the state it had just moved to. What a producer does with it is switch its
     * expensive source on and off: register a native listener, start a task, warm a cache. A producer
     * of a whole family names the concrete types it posts, not their base.
     */
    @SafeVarargs
    public final ECListenerWatch watchListeners(Runnable onFirstListener, @Nullable Runnable onLastListenerGone, Class<? extends IECEvent>... eventTypes) {
        return watchListeners(null, onFirstListener, onLastListenerGone, eventTypes);
    }

    /**
     * As {@link #watchListeners(Runnable, Runnable, Class[])}, owned by {@code plugin}: the watch is
     * stopped with the rest of that plugin's when it shuts down ({@link #unsubscribeAll(ECPluginData)}).
     */
    @SafeVarargs
    public final ECListenerWatch watchListeners(@Nullable ECPluginData plugin, Runnable onFirstListener, @Nullable Runnable onLastListenerGone, Class<? extends IECEvent>... eventTypes) {
        Objects.requireNonNull(onFirstListener, "'onFirstListener' cannot be null when watching listeners on an ECEventBus!");
        Objects.requireNonNull(eventTypes, "'eventTypes' cannot be null when watching listeners on an ECEventBus!");
        if (eventTypes.length == 0) {
            throw new IllegalArgumentException("A listener watch needs at least one event type to follow - "
                    + "no types would be a watch that never fires.");
        }
        for (Class<? extends IECEvent> eventType : eventTypes) {
            Objects.requireNonNull(eventType, "'eventTypes' cannot hold a null when watching listeners on an ECEventBus!");
        }

        Watch watch = new Watch(
                Collections.unmodifiableList(new ArrayList<Class<? extends IECEvent>>(Arrays.asList(eventTypes))),
                plugin,
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
                    Throwable failure;
                    try {
                        action.run();
                        continue;
                    } catch (Throwable t) {
                        failure = t;
                    }
                    //outside the try on purpose: what the exception handler throws belongs to the caller
                    exceptionHandler.onWatchFailure(watch, now, failure);
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

    private void addSubscriptions(List<Subscription<?>> toAdd) {
        synchronized (mutex) {
            for (Subscription<?> subscription : toAdd) {
                subscription.sequence = nextSequence++;
                subscriptionsByType.computeIfAbsent(subscription.eventType, type -> new ArrayList<>()).add(subscription);
            }
            dispatchIndex = Collections.emptyMap();
        }
        refreshListenerWatches();
    }

    private static final Comparator<Subscription<?>> BY_SEQUENCE = Comparator.comparingLong(subscription -> subscription.sequence);

    //Priority first; a tie keeps the order the subscriptions were taken in, whatever class each one
    //named - the sequence decides that, not the order the ancestors were walked in.
    private static final Comparator<Subscription<?>> BY_PRIORITY_THEN_SEQUENCE =
            Comparator.<Subscription<?>>comparingInt(subscription -> subscription.options.getPriority()).thenComparing(BY_SEQUENCE);

    private Subscription<?>[] planFor(Class<?> eventClass) {
        Subscription<?>[] plan = dispatchIndex.get(eventClass);
        if (plan != null) {
            return plan;
        }

        synchronized (mutex) {
            plan = dispatchIndex.get(eventClass);
            if (plan != null) {
                return plan;
            }

            List<Subscription<?>> matching = new ArrayList<>();
            for (Class<?> ancestor : ancestorsOf(eventClass)) {
                List<Subscription<?>> declared = subscriptionsByType.get(ancestor);
                if (declared == null) continue;
                for (Subscription<?> subscription : declared) {
                    //an exact subscription hears the class it named and nothing below it
                    if (subscription.options.isExact() && ancestor != eventClass) continue;
                    matching.add(subscription);
                }
            }
            matching.sort(BY_PRIORITY_THEN_SEQUENCE);
            plan = matching.toArray(new Subscription<?>[0]);

            Map<Class<?>, Subscription<?>[]> updated = new HashMap<>(dispatchIndex);
            updated.put(eventClass, plan);
            dispatchIndex = updated;
            return plan;
        }
    }

    /**
     * {@code eventClass} first, then every superclass and interface reachable from it - each once,
     * however many paths lead to it - which is what makes a subscription to a marker interface hear
     * every event that carries it, and hear it once.
     */
    static List<Class<?>> ancestorsOf(Class<?> eventClass) {
        Set<Class<?>> ancestors = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(eventClass);
        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (!ancestors.add(type)) continue;
            if (type.getSuperclass() != null) {
                pending.add(type.getSuperclass());
            }
            Collections.addAll(pending, type.getInterfaces());
        }
        return new ArrayList<>(ancestors);
    }

    private final class Subscription<T extends IECEvent> implements ECEventSubscription<T> {
        final Class<T> eventType;
        final ECSubscribeOptions options;
        final Object subscriber;              // the ECEventSubscriber, or the listener an annotated method belongs to
        final Method method;                  // the annotated method; null for a functional subscription
        final ECEventSubscriber<IECEvent> action;
        long sequence;                        // assigned under mutex when added: the subscription order
        volatile boolean active = true;

        Subscription(Class<T> eventType, ECSubscribeOptions options, Object subscriber, @Nullable Method method, ECEventSubscriber<IECEvent> action) {
            this.eventType = eventType;
            this.options = options;
            this.subscriber = subscriber;
            this.method = method;
            this.action = action;
        }

        @Override
        public Class<T> getEventType() {
            return eventType;
        }

        @Override
        public ECSubscribeOptions getOptions() {
            return options;
        }

        @Override
        public Object getSubscriber() {
            return subscriber;
        }

        @Override
        public void unsubscribe() {
            unsubscribeIf(candidate -> candidate == this);
        }

        @Override
        public boolean isActive() {
            return active;
        }

        /** {@code "ShopListener#onLogin on ECPlayerFullyLoggedInEvent [LATE, plugin=Shop]"} */
        @Override
        public String toString() {
            String who = method != null
                    ? nameOf(subscriber.getClass()) + "#" + method.getName()
                    : subscriber.getClass().getName();
            return who + " on " + eventType.getSimpleName() + " [" + options + "]";
        }
    }

    /** The simple name, or the full one for a class that has none (anonymous). */
    private static String nameOf(Class<?> type) {
        String simpleName = type.getSimpleName();
        return simpleName.isEmpty() ? type.getName() : simpleName;
    }

    private final class Watch implements ECListenerWatch {
        final List<Class<? extends IECEvent>> eventTypes;
        final ECPluginData plugin;          // null when the watch is not owned by a plugin
        final String pluginName;            // ownership is by name, like the drain compares it
        final Runnable onFirstListener;
        final Runnable onLastListenerGone;  // null when the producer has nothing to undo
        //Both only ever written by the single evaluation pass; volatile so a read from any thread
        //sees what the last callback was told.
        volatile boolean present;
        volatile boolean active = true;

        Watch(List<Class<? extends IECEvent>> eventTypes, @Nullable ECPluginData plugin, Runnable onFirstListener, @Nullable Runnable onLastListenerGone) {
            this.eventTypes = eventTypes;
            this.plugin = plugin;
            this.pluginName = plugin == null ? null : plugin.getMetaInfo().getName();
            this.onFirstListener = onFirstListener;
            this.onLastListenerGone = onLastListenerGone;
        }

        @Override
        public Collection<Class<? extends IECEvent>> getEventTypes() {
            return eventTypes;
        }

        @Override
        public ECPluginData getPlugin() {
            return plugin;
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

        /** {@code "[ECPlayerChangeChunkEvent] plugin=EverNifeCore present"} */
        @Override
        public String toString() {
            String types = eventTypes.stream().map(Class::getSimpleName).collect(Collectors.joining(", ", "[", "]"));
            return types + (pluginName != null ? " plugin=" + pluginName : "") + (present ? " present" : " absent");
        }
    }

}
