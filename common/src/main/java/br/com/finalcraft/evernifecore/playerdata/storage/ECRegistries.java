package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.RefRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The process-wide reference registries: one global root and a lazily created child per plugin,
 * <b>stable in identity across core storage reloads</b>. A reload never swaps these objects - it
 * replaces their CONTENT, manager by manager (see {@code RefRegistry.replace}), so a {@code Ref}
 * alive from before the reload keeps resolving through the same registry and finds the new
 * generation's manager on its next access. That stability is why a plugin-owned {@code ECStorage}
 * opens once and never needs re-opening after a core reload.
 *
 * <p>Each plugin gets its own child {@link RefRegistry} (parented to the global) so two plugins
 * can register a manager for the same entity type without colliding, while still resolving shared
 * entities published in the global. A {@code null} plugin (sections registered through the legacy
 * {@code (ecPluginData, cls)} call site with no plugin data, or the test fixtures) falls back to
 * the global registry.</p>
 */
public final class ECRegistries {

    /** The one instance the whole process shares; a PlayerController reload reuses it, never replaces it. */
    private static final ECRegistries INSTANCE = new ECRegistries();

    public static ECRegistries get() {
        return INSTANCE;
    }

    private final RefRegistry global = new RefRegistry();
    private final Map<ECPluginData, RefRegistry> perPlugin = new ConcurrentHashMap<>();

    private ECRegistries() {
    }

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

    /**
     * Empties every registry's CONTENT - the global's registrations and every per-plugin child,
     * which is also dropped - while the registry objects themselves stay untouched (their identity
     * is the stability contract). The shutdown counterpart of the reload's manager-by-manager
     * replace: after it nothing resolves, but a registry captured by a codec or a {@code Ref} is
     * not poisoned - a later bootstrap in the same JVM (tests, a full disable/enable cycle)
     * repopulates the same instances.
     */
    public void clearAll() {
        for (RefRegistry child : perPlugin.values()) {
            child.clear();
        }
        perPlugin.clear();
        global.clear();
    }
}
