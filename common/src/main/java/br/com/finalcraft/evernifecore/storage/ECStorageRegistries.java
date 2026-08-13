package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.RefRegistry;

import java.util.function.Function;

/**
 * Bridge that lets an {@link ECStorage} share a plugin's {@link RefRegistry} with that plugin's PlayerData
 * PDSections - so a {@code Ref} inside a PDSection can resolve an entity that lives in the plugin's own
 * ECStorage. The plugin's PDSection managers and its ECStorage managers then register in the SAME registry
 * (the plugin's child, parented to the framework-wide {@code global}), which is what {@code Ref} resolution
 * walks. The registries are stable in identity across core reloads, so the answer this bridge gives at
 * open time stays the right registry for the life of the plugin.
 *
 * <p>Kept in the {@code storage} package - and populated by {@code PlayerController} at bootstrap - so
 * {@link ECStorage} reaches the per-plugin registry WITHOUT the storage layer depending on the playerdata
 * layer. Until the provider is wired (a pure-JUnit storage test, or before the PlayerData bootstrap runs),
 * {@link #of(ECPluginData)} returns {@code null} and an {@code ECStorage} falls back to a private registry.</p>
 */
public final class ECStorageRegistries {

    /** Resolves a plugin's (identity-stable) child registry; {@code null} until the bootstrap wires it. */
    private static volatile Function<ECPluginData, RefRegistry> provider;

    private ECStorageRegistries() {
    }

    /** Wired once by {@code PlayerController}. */
    public static void setProvider(Function<ECPluginData, RefRegistry> registryProvider) {
        provider = registryProvider;
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
