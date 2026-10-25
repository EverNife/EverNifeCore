package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Every live {@link TypeRegistry}, so a plugin leaving takes its types out of all of them at once. */
public final class TypeRegistries {

    //weak: a registry nobody references any more (a test's) leaves with its types
    private static final Set<TypeRegistry<?>> REGISTRIES = Collections.newSetFromMap(new WeakHashMap<TypeRegistry<?>, Boolean>());

    private TypeRegistries() {
    }

    static void track(TypeRegistry<?> registry) {
        synchronized (REGISTRIES) {
            REGISTRIES.add(registry);
        }
    }

    /** Every live registry, in no particular order. */
    public static List<TypeRegistry<?>> getAll() {
        synchronized (REGISTRIES) {
            return new ArrayList<>(REGISTRIES);
        }
    }

    /** {@link TypeRegistry#unregisterAll} on every live registry: the types {@code owner} registered and its pending definitions leave. */
    public static void unregisterAll(ECPluginData owner) {
        for (TypeRegistry<?> registry : getAll()) {
            registry.unregisterAll(owner);
        }
    }
}
