package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Delivers {@link IECEvent}s to whoever subscribed on this bus and, on the global bus only, mirrors
 * them into every registered {@link ECNativeAudience}.
 *
 * <p>A post runs the local phase first - every matching handler in priority order - and only then
 * the audiences, in registration order. A handler or an audience that throws is logged and skipped:
 * it never breaks the producer nor the ones queued behind it.</p>
 *
 * <p>{@link #global()} is the bus platforms mirror from and plugins subscribe to; it lives as long
 * as the classloader does. {@link #create()} builds a scoped bus for a subsystem's own traffic - it
 * never mirrors, so an audience added to it is accepted and never called.</p>
 */
public class ECEventBus {

    private static final Logger LOGGER = Logger.getLogger("ECEventBus");

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
            short priority = annotation.priorityValue() != -1
                    ? annotation.priorityValue()
                    : annotation.priority().getValue();

            toAdd.add(new Handler(parameterTypes[0], listener, null, priority, event -> invoker.invoke(listener, event)));
        }

        if (toAdd.isEmpty()) return;
        addHandlers(toAdd);
    }

    /** Drops every handler {@link #register(Object)} took from this listener. */
    public void unregister(Object listener) {
        if (listener == null) return;
        removeHandlers(handler -> handler.owner == listener);
    }

    /** Drops every subscription opened in this plugin's name - the shutdown drain. */
    public void unsubscribeAll(ECPluginData plugin) {
        Objects.requireNonNull(plugin, "'plugin' cannot be null when draining its ECEventBus subscriptions!");
        String pluginName = plugin.getMetaInfo().getName();
        removeHandlers(handler -> pluginName.equals(handler.pluginName));
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

        if (mirroring) {
            for (ECNativeAudience audience : audiences) {
                try {
                    if (audience.hasListeners(event)) {
                        audience.dispatch(event);
                    }
                } catch (Throwable t) {
                    //The SPI says an audience swallows its own errors; this is the belt to that suspender.
                    LOGGER.log(Level.SEVERE, "[ECEventBus] Native audience '" + audience.name() + "' failed on "
                            + event.getClass().getName() + "; the remaining audiences still run.", t);
                }
            }
        }

        return event;
    }

    /** Delivers {@code event} to this bus only: no audience is consulted, whatever the event is. */
    public <T extends IECEvent> T postLocal(T event) {
        if (event == null) return null;
        deliverLocal(event);
        return event;
    }

    private void deliverLocal(IECEvent event) {
        for (Handler handler : planFor(event.getClass())) {
            try {
                handler.action.accept(event);
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "[ECEventBus] Handler " + handler.describe() + " failed on "
                        + event.getClass().getName() + "; the remaining handlers still run.", t);
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
            for (int index = 0; index < audiences.size(); index++) {
                if (name.equals(audiences.get(index).name())) {
                    //Replacing in place keeps the delivery order stable across a re-enable, instead of
                    //shuffling the audience to the back of the queue.
                    audiences.set(index, audience);
                    return;
                }
            }
            audiences.add(audience);
        }
    }

    /** Removes the audience answering to {@code name}, if any. */
    public void removeNativeAudience(String name) {
        if (name == null) return;
        synchronized (mutex) {
            audiences.removeIf(audience -> name.equals(audience.name()));
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
    }

    private void removeHandlers(Predicate<Handler> filter) {
        synchronized (mutex) {
            boolean removedAny = false;
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
        final Consumer<IECEvent> action;
        volatile boolean active = true;

        Handler(Class<?> eventType, Object owner, String pluginName, short priority, Consumer<IECEvent> action) {
            this.eventType = eventType;
            this.owner = owner;
            this.pluginName = pluginName;
            this.priority = priority;
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

}
