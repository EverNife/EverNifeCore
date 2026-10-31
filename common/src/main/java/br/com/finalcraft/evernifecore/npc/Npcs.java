package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageWatch;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The process-wide registry of {@link NpcProvider}s, and the one place an {@link NpcRef} is answered: each lookup goes to
 * the provider the reference names, and finds nothing while that provider is unregistered or unavailable. Providers
 * are asked in registration order.
 */
public final class Npcs {

    private static final Map<String, Registration> PROVIDERS = new LinkedHashMap<>();
    //read on every actor a platform builds: a copy made on each change, never on a read
    private static volatile List<NpcProvider> snapshot = Collections.emptyList();

    private Npcs() {
    }

    /**
     * Registers {@code provider} in {@code owner}'s name. Its {@link NpcProvider#start()} runs while
     * {@link EventSource#INTERACT_NPC} is needed and {@link NpcProvider#stop()} once it is not.
     *
     * @throws IllegalStateException when another provider already answers for the same id - the message names both owners
     */
    public static void register(ECPluginData owner, NpcProvider provider) {
        String id = NpcRef.of(provider.getId(), "x").getProvider();
        synchronized (PROVIDERS) {
            Registration taken = PROVIDERS.get(id);
            if (taken != null) {
                throw new IllegalStateException("The npc provider '" + id + "' of " + owner.getMetaInfo().getName() + " collides with the one "
                        + taken.owner.getMetaInfo().getName() + " already registered: one provider per id - pick another id, or leave"
                        + " the NPCs of that plugin to " + taken.owner.getMetaInfo().getName() + ".");
            }
            PROVIDERS.put(id, new Registration(owner, provider));
            refreshSnapshot();
        }
        //outside the lock: a source already needed runs start() at once
        Registration registration = PROVIDERS.get(id);
        registration.watch = EventSourceUsage.get().watch(owner, EventSource.INTERACT_NPC, provider::start, provider::stop);
    }

    /** Forgets every provider {@code owner} registered, stopping the ones started. Part of the owner's shutdown. */
    public static void unregisterAll(ECPluginData owner) {
        List<Registration> removed = new ArrayList<>();
        synchronized (PROVIDERS) {
            for (Iterator<Registration> it = PROVIDERS.values().iterator(); it.hasNext(); ) {
                Registration registration = it.next();
                if (registration.owner == owner) {
                    removed.add(registration);
                    it.remove();
                }
            }
            refreshSnapshot();
        }
        for (Registration registration : removed) {
            UsageWatch watch = registration.watch;
            if (watch != null) {
                boolean started = watch.isNeeded();
                watch.stop();
                if (started) {
                    registration.provider.stop();
                }
            }
        }
    }

    /** The provider registered for {@code id}, available or not. */
    public static Optional<NpcProvider> provider(String id) {
        synchronized (PROVIDERS) {
            Registration registration = PROVIDERS.get(id);
            return registration == null ? Optional.empty() : Optional.of(registration.provider);
        }
    }

    /** Every provider registered, available or not, in registration order. */
    public static Collection<NpcProvider> getProviders() {
        return snapshot;
    }

    private static void refreshSnapshot() {
        List<NpcProvider> providers = new ArrayList<>();
        for (Registration registration : PROVIDERS.values()) {
            providers.add(registration.provider);
        }
        snapshot = Collections.unmodifiableList(providers);
    }

    /** Which NPC {@code entity} is, by the first available provider that knows it; empty for an entity none knows. */
    public static Optional<NpcRef> resolve(NativeEntity entity) {
        for (NpcProvider provider : getProviders()) {
            if (provider.isAvailable()) {
                Optional<NpcRef> ref = provider.resolve(entity);
                if (ref.isPresent()) {
                    return ref;
                }
            }
        }
        return Optional.empty();
    }

    /** The NPC's entity now - see {@link NpcProvider#find} for the thread; empty when absent or its provider is not here. */
    public static Optional<NativeEntity> find(NpcRef ref) {
        Optional<NpcProvider> provider = available(ref);
        return provider.isPresent() ? provider.get().find(ref) : Optional.empty();
    }

    /** Where the NPC stands, as its plugin stores it; empty when unknown or its provider is not here. */
    public static Optional<WorldLocPos> getLocation(NpcRef ref) {
        Optional<NpcProvider> provider = available(ref);
        return provider.isPresent() ? provider.get().getLocation(ref) : Optional.empty();
    }

    /** The NPC's display name; empty when unknown or its provider is not here. */
    public static Optional<String> getName(NpcRef ref) {
        Optional<NpcProvider> provider = available(ref);
        return provider.isPresent() ? provider.get().getName(ref) : Optional.empty();
    }

    /**
     * Fails loud for a definition that names an NPC of a provider nobody registered - what a condition, a trigger or a
     * waypoint checks when it is read.
     *
     * @throws NpcProviderException naming the reference, the provider, the ones registered here, and what to do
     */
    public static NpcRef requireProvider(NpcRef ref) {
        if (!provider(ref.getProvider()).isPresent()) {
            List<String> registered = new ArrayList<>();
            for (NpcProvider provider : getProviders()) {
                registered.add(provider.getId());
            }
            throw new NpcProviderException("The npc reference " + ref + " names the provider '" + ref.getProvider() + "', but no plugin"
                    + " registered it on this server (registered: " + (registered.isEmpty() ? "none" : String.join(", ", registered))
                    + "). Install the plugin that provides it or fix the reference.");
        }
        return ref;
    }

    private static Optional<NpcProvider> available(NpcRef ref) {
        Optional<NpcProvider> provider = provider(ref.getProvider());
        return provider.isPresent() && provider.get().isAvailable() ? provider : Optional.empty();
    }

    private static final class Registration {

        private final ECPluginData owner;
        private final NpcProvider provider;
        private volatile UsageWatch watch;

        private Registration(ECPluginData owner, NpcProvider provider) {
            this.owner = owner;
            this.provider = provider;
        }
    }
}
