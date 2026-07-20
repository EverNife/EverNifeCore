package br.com.finalcraft.evernifecore.api.eventhandler;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Framework-agnostic registry and dispatcher for {@link IECEvent}s.
 * <p>
 * A listener registers {@link ECEventHandler}-annotated methods whose single parameter is
 * assignable to {@link IECEvent}; {@link #post(IECEvent)} then invokes every matching handler in
 * priority order. The implementation lives here (in {@code common}) rather than in a platform
 * dispatcher because {@code IECEvent} is platform-neutral and the dispatch is identical on every
 * platform - the Bukkit and Hytale dispatchers inherit this behavior. Handlers annotated for a
 * platform's own native event type (e.g. Hytale's {@code IEvent}) are a disjoint set handled by
 * that platform's listener registration and are ignored here.
 */
public class ECEventDispatcher {

    private static final class Handler {
        final Class<?> eventType;
        final Object listener;
        final MethodInvoker<?> invoker;
        final short priority;

        Handler(Class<?> eventType, Object listener, MethodInvoker<?> invoker, short priority) {
            this.eventType = eventType;
            this.listener = listener;
            this.invoker = invoker;
            this.priority = priority;
        }
    }

    //Written under `handlers` monitor on register/unregister; read lock-free on the post() hot
    //path via this volatile immutable snapshot (copy-on-write).
    private volatile List<Handler> snapshot = new ArrayList<>();
    private final List<Handler> handlers = new ArrayList<>();

    public void register(Object listener) {
        if (listener == null) return;

        List<MethodInvoker<?>> annotatedMethods = FCReflectionUtil.getMethods()
                .getMethods(listener.getClass(), method -> method.getAnnotation(ECEventHandler.class) != null)
                .collect(java.util.stream.Collectors.toList());

        List<Handler> toAdd = new ArrayList<>();
        for (MethodInvoker<?> invoker : annotatedMethods) {
            Method method = invoker.getMethod();
            Class<?>[] parameterTypes = method.getParameterTypes();

            //Only single-parameter IECEvent handlers belong here; anything else (native platform
            //events, malformed signatures) is silently skipped - a platform listener owns those.
            if (parameterTypes.length != 1 || !IECEvent.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }

            ECEventHandler annotation = method.getAnnotation(ECEventHandler.class);
            short priority = annotation.priorityValue() != -1
                    ? annotation.priorityValue()
                    : annotation.priority().getValue();

            toAdd.add(new Handler(parameterTypes[0], listener, invoker, priority));
        }

        if (toAdd.isEmpty()) return;

        synchronized (handlers) {
            handlers.addAll(toAdd);
            rebuildSnapshot();
        }
    }

    public void unregister(Object listener) {
        if (listener == null) return;
        synchronized (handlers) {
            if (handlers.removeIf(handler -> handler.listener == listener)) {
                rebuildSnapshot();
            }
        }
    }

    public void post(IECEvent event) {
        if (event == null) return;
        //ignoreCancelled() is a no-op for IECEvent: there is no cancellation contract on this marker.
        for (Handler handler : snapshot) {
            if (handler.eventType.isInstance(event)) {
                handler.invoker.invoke(handler.listener, event);
            }
        }
    }

    private void rebuildSnapshot() {
        List<Handler> copy = new ArrayList<>(handlers);
        //Lower priority value runs first (FIRST -> LAST), matching the ECEventPriority ordering.
        copy.sort(Comparator.comparingInt(handler -> handler.priority));
        this.snapshot = copy;
    }

}
