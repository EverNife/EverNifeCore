package br.com.finalcraft.evernifecore.listeners.base;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-plugin registry of the {@link ECListener}s registered through {@link ECListener#register},
 * so the whole set of a plugin can be torn down at once ({@link ECListener#unregisterAll}). Kept in
 * its own class so the backing map stays private (an interface cannot hold a private field).
 */
final class ECListenerRegistry {

    private static final Map<String, Set<ECListener>> BY_PLUGIN = new ConcurrentHashMap<>();

    private ECListenerRegistry() {
    }

    static void track(String pluginName, ECListener listener) {
        BY_PLUGIN.computeIfAbsent(pluginName, k -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    /** Drops the listener from whatever plugin set holds it (used on individual unregister). */
    static void forget(ECListener listener) {
        for (Set<ECListener> set : BY_PLUGIN.values()) {
            set.remove(listener);
        }
    }

    /** A read-only copy of the listeners currently tracked for the plugin. */
    static Set<ECListener> snapshot(String pluginName) {
        Set<ECListener> set = BY_PLUGIN.get(pluginName);
        return set == null ? Collections.emptySet() : new LinkedHashSet<>(set);
    }

    /** Removes and returns the plugin's whole set (used to unregister them all). */
    static Set<ECListener> drain(String pluginName) {
        Set<ECListener> set = BY_PLUGIN.remove(pluginName);
        return set == null ? Collections.emptySet() : set;
    }
}
