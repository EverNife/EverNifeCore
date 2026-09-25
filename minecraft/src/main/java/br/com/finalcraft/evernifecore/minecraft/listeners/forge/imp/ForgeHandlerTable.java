package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The handlers an adapter dispatches Forge events to by hand, keyed by the event type each one declared.
 *
 * <p>Safe to change while events are being posted on another thread: every change publishes a new
 * immutable table, and a post reads whichever table was current when it started - a handler removed
 * during a post may still receive that one event, never a {@code ConcurrentModificationException}.</p>
 */
final class ForgeHandlerTable {

    private static final class Snapshot {
        private final Map<Class<?>, List<Consumer<?>>> byDeclaredType;
        private final Map<Class<?>, List<Consumer<?>>> byEventClass = new ConcurrentHashMap<>();

        private Snapshot(Map<Class<?>, List<Consumer<?>>> byDeclaredType) {
            this.byDeclaredType = byDeclaredType;
        }
    }

    private volatile Snapshot current = new Snapshot(Collections.<Class<?>, List<Consumer<?>>>emptyMap());

    synchronized void add(Class<?> declaredType, Consumer<?> handler) {
        Map<Class<?>, List<Consumer<?>>> next = copy(current.byDeclaredType);
        next.computeIfAbsent(declaredType, k -> new ArrayList<>()).add(handler);
        current = new Snapshot(next);
    }

    /** Removes exactly these handler instances; handlers that are no longer here are ignored. */
    synchronized void remove(Map<Class<?>, List<Consumer<?>>> handlers) {
        Map<Class<?>, List<Consumer<?>>> next = copy(current.byDeclaredType);
        for (Map.Entry<Class<?>, List<Consumer<?>>> entry : handlers.entrySet()) {
            List<Consumer<?>> registered = next.get(entry.getKey());
            if (registered == null) {
                continue;
            }
            for (Consumer<?> handler : entry.getValue()) {
                registered.removeIf(candidate -> candidate == handler);
            }
            if (registered.isEmpty()) {
                next.remove(entry.getKey());
            }
        }
        current = new Snapshot(next);
    }

    /** Every handler whose declared type {@code eventClass} is assignable to, in registration order. */
    List<Consumer<?>> handlersFor(Class<?> eventClass) {
        Snapshot snapshot = current;
        return snapshot.byEventClass.computeIfAbsent(eventClass, type -> {
            List<Consumer<?>> matching = new ArrayList<>();
            for (Map.Entry<Class<?>, List<Consumer<?>>> entry : snapshot.byDeclaredType.entrySet()) {
                if (entry.getKey().isAssignableFrom(type)) {
                    matching.addAll(entry.getValue());
                }
            }
            return Collections.unmodifiableList(matching);
        });
    }

    private static Map<Class<?>, List<Consumer<?>>> copy(Map<Class<?>, List<Consumer<?>>> source) {
        Map<Class<?>, List<Consumer<?>>> copy = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, List<Consumer<?>>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}
