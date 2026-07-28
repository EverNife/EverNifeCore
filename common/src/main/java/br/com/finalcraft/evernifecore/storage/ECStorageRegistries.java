package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.RefRegistry;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bridge that lets an {@link ECStorage} share a plugin's {@link RefRegistry} with that plugin's PlayerData
 * PDSections - so a {@code Ref} inside a PDSection can resolve an entity that lives in the plugin's own
 * ECStorage. The plugin's PDSection managers and its ECStorage managers then register in the SAME registry
 * (the plugin's child, parented to the framework-wide {@code global}), which is what {@code Ref} resolution
 * walks.
 *
 * <p>Kept in the {@code storage} package - and populated by {@code PlayerController} at bootstrap - so
 * {@link ECStorage} reaches the per-plugin registry WITHOUT the storage layer depending on the playerdata
 * layer. Until the provider is wired (a pure-JUnit storage test, or before the PlayerData bootstrap runs),
 * {@link #of(ECPluginData)} returns {@code null} and an {@code ECStorage} falls back to a private registry.</p>
 */
public final class ECStorageRegistries {

    /** Resolves the CURRENT controller instance's child registry for a plugin; swapped-safe (resolves lazily). */
    private static volatile Function<ECPluginData, RefRegistry> provider;

    /** Answers whether a plugin registered a storage-reload callback; see {@link #hasReloadHook}. */
    private static volatile Predicate<ECPluginData> reloadHookProbe;

    private ECStorageRegistries() {
    }

    /** Wired once by {@code PlayerController} (its lambda resolves the live controller instance each call). */
    public static void setProvider(Function<ECPluginData, RefRegistry> registryProvider) {
        provider = registryProvider;
    }

    /** Wired once by {@code PlayerController}, alongside {@link #setProvider}. */
    public static void setReloadHookProbe(Predicate<ECPluginData> probe) {
        reloadHookProbe = probe;
    }

    /**
     * Whether {@code plugin} registered a storage-reload callback. An {@link ECStorage} asks this when it
     * opens: without a callback, nothing re-runs the plugin's storage setup after a reload swaps the
     * per-plugin registries, and the handle is left wired to a detached one.
     *
     * <p>{@code true} when no probe is wired yet - the answer is unknown, and a warning built on a guess
     * would fire on every plugin-less/bootstrap-time open.</p>
     */
    public static boolean hasReloadHook(ECPluginData plugin) {
        Predicate<ECPluginData> current = reloadHookProbe;
        return current == null || plugin == null || current.test(plugin);
    }

    /**
     * The shared child {@link RefRegistry} for {@code plugin} (the namespace its PDSections resolve through),
     * or {@code null} when no provider is wired yet or {@code plugin} is {@code null} - in which case the
     * caller keeps its own private registry.
     */
    public static RefRegistry of(ECPluginData plugin) {
        Function<ECPluginData, RefRegistry> current = provider;
        return (current != null && plugin != null) ? current.apply(plugin) : null;
    }
}
