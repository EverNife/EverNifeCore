package br.com.finalcraft.evernifecore.hytale.inventory.extrainvs;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.factory.IExtraInvFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The extra inventories (a mod's backpack, baubles, ...) a player inventory snapshot carries besides the vanilla one,
 * each registered by the plugin that supports it and released when that plugin leaves.
 */
public class ExtraInvManager {

    private static final Map<String, Owned> FACTORIES = new LinkedHashMap<>();

    private static final class Owned {
        final ECPluginData owner;
        final IExtraInvFactory<?> factory;

        Owned(ECPluginData owner, IExtraInvFactory<?> factory) {
            this.owner = owner;
            this.factory = factory;
        }
    }

    /**
     * Registers {@code factory} under its id (case-insensitive) for {@code owner}. The same owner registering the id again
     * (a reload) replaces its own factory.
     *
     * @throws IllegalStateException when another plugin already registered that id
     */
    public static synchronized void registerFactory(@Nonnull ECPluginData owner, @Nonnull IExtraInvFactory<?> factory) {
        Objects.requireNonNull(owner, "'owner' cannot be null when registering an extra inventory factory!");
        String id = factory.getId().toLowerCase(Locale.ROOT);
        Owned taken = FACTORIES.get(id);
        if (taken != null && taken.owner != owner) {
            throw new IllegalStateException("The extra inventory '" + id + "' is already registered by "
                    + taken.owner.getMetaInfo().getName() + ", so " + owner.getMetaInfo().getName() + " cannot register it too."
                    + " Give the factory another id, or drop one of the two plugins' support for that inventory.");
        }
        FACTORIES.put(id, new Owned(owner, factory));
    }

    /** Drops every factory {@code owner} registered; the core calls it as that plugin leaves. */
    public static synchronized void unregisterAll(@Nonnull ECPluginData owner) {
        FACTORIES.values().removeIf(owned -> owned.owner == owner);
    }

    /** Every factory, in registration order; a snapshot. */
    public static synchronized Collection<IExtraInvFactory<?>> getAllFactories() {
        Collection<IExtraInvFactory<?>> factories = new ArrayList<>(FACTORIES.size());
        for (Owned owned : FACTORIES.values()) {
            factories.add(owned.factory);
        }
        return Collections.unmodifiableCollection(factories);
    }

    public static synchronized @Nullable IExtraInvFactory<?> getFactory(String factoryId) {
        Owned owned = FACTORIES.get(factoryId.toLowerCase(Locale.ROOT));
        return owned == null ? null : owned.factory;
    }
}
