package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.RefRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reference registries owned by a {@link br.com.finalcraft.evernifecore.playerdata.PlayerController}
 * instance: one global root and a lazily created child per plugin.
 *
 * <p>Each plugin gets its own child {@link RefRegistry} (parented to the global) so two
 * plugins can register a manager for the same entity type without colliding, while still
 * resolving shared entities published in the global. A {@code null} plugin (sections
 * registered through the legacy {@code (ecPluginData, cls)} call site with no plugin data,
 * or the test fixtures) falls back to the global registry.</p>
 */
public final class ECRegistries {

    private final RefRegistry global = new RefRegistry();
    private final Map<ECPluginData, RefRegistry> perPlugin = new ConcurrentHashMap<>();

    public RefRegistry global() {
        return global;
    }

    /** The plugin's child registry (created on first use); the global when {@code plugin} is null. */
    public RefRegistry of(ECPluginData plugin) {
        if (plugin == null) {
            return global;
        }
        return perPlugin.computeIfAbsent(plugin, p -> new RefRegistry(global));
    }

    /**
     * Drops the plugin's child registry, so a disabled plugin's classes (and its classloader)
     * are no longer held alive through this map. A {@code null} plugin is a no-op (it never had
     * a child - it resolves to the global).
     */
    public void drop(ECPluginData plugin) {
        if (plugin != null) {
            perPlugin.remove(plugin);
        }
    }
}
