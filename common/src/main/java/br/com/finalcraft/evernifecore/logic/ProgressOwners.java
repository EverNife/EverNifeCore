package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The owner kinds and who answers each. The core answers {@code player}, {@code global}, {@code region} and
 * {@code world}; a plugin adds a kind ({@code group}) by registering its provider, keyed {@code <plugin>:<kind>}.
 */
public final class ProgressOwners {

    private static final TypeRegistry<ProgressOwnerProvider> REGISTRY = TypeRegistry.of(ProgressOwnerProvider.class, "owner");
    //weak: a descriptor that left the registry takes its provider with it
    private static final Map<TypeDescriptor<ProgressOwnerProvider>, ProgressOwnerProvider> PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<TypeDescriptor<ProgressOwnerProvider>, ProgressOwnerProvider>());

    private ProgressOwners() {
    }

    public static TypeRegistry<ProgressOwnerProvider> registry() {
        return REGISTRY;
    }

    /**
     * Registers {@code provider} for its kind under {@code owner}; it leaves with {@code owner}'s shutdown.
     *
     * @throws IllegalStateException when the kind is already answered under the same key
     */
    public static void register(ECPluginData owner, ProgressOwnerProvider provider) {
        String key = owner.getMetaInfo().getName() + ":" + provider.getKind();
        TypeDescriptor<ProgressOwnerProvider> descriptor = TypeDescriptor.<ProgressOwnerProvider>of(key)
                .describe("Progress and balances owned by a " + provider.getKind() + ".")
                .factory(params -> provider)
                .build();
        PROVIDERS.put(descriptor, provider);
        REGISTRY.register(owner, descriptor);
    }

    /** Who answers {@code kind}; empty when nobody registered it (or two plugins did and neither wins). */
    public static Optional<ProgressOwnerProvider> find(String kind) {
        return REGISTRY.find(kind).map(PROVIDERS::get);
    }
}
