package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A plugin's access to the ONE backend every server of the network shares - the one under
 * {@code network.storage-backend-id}, alongside the account registry and the network cooldowns. Use it
 * for data that must look the same from every server: a guild bank, a network-wide leaderboard, a
 * cross-server market.
 *
 * <pre>{@code
 * ECNetworkStorage network = ECNetworkStorage.of(this.getEcPluginData());
 * CachingManager<UUID, GuildBank> banks = network.manager(BANKS, CachePolicy.always());
 * // in onDisable:
 * network.release();
 * }</pre>
 *
 * <p>Unlike {@link ECStorage} this captures nothing and has no {@code close()}: the network storage
 * belongs to the PlayerController and is rebuilt on every core reload, so it is resolved live on each
 * call. What a plugin owns is its registrations, which {@link #release()} gives back.
 *
 * <p>{@link #manager} and {@link #repository} claim the descriptor's collection first, so two plugins
 * reaching for one name fail deterministically instead of writing into the same table. The registry is
 * shared with the plugin's PDSections and {@code ECStorage}, which hold one manager per entity type -
 * so each type gets exactly one home.
 */
public final class ECNetworkStorage {

    /** Resolves the LIVE network access; wired once by {@code PlayerController}. */
    private static volatile Supplier<NetworkAccess> accessProvider;

    private final ECPluginData plugin;
    private final String owner;

    /** Only what THIS handle registered, so {@link #release} never touches a PDSection's registration. */
    private final Set<Class<?>> registeredTypes = new LinkedHashSet<>();
    private final Set<String> claimedCollections = new LinkedHashSet<>();

    private ECNetworkStorage(ECPluginData plugin, String owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    /**
     * A handle onto the network backend for {@code plugin}. Opens no connection and reads no config of
     * its own: the backend is already up, chosen by the admin under {@code network.storage-backend-id}.
     *
     * @throws StorageUnavailableException when the PlayerController has not bootstrapped yet
     */
    public static ECNetworkStorage of(ECPluginData plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("ECNetworkStorage needs the plugin it belongs to -"
                    + " its name is what owns the collection claims");
        }
        access(); //fail here, at the call an author can see, rather than on the first manager()
        return new ECNetworkStorage(plugin, "plugin:" + plugin.getMetaInfo().getName());
    }

    /** Wired once by {@code PlayerController}; the supplier resolves the live instance on each call. */
    public static void setAccessProvider(Supplier<NetworkAccess> provider) {
        accessProvider = provider;
    }

    /** The backend id the admin named under {@code network.storage-backend-id}. */
    public String backendName() {
        return access().backendName();
    }

    /** The live network {@link Storage}. Resolved fresh on every call - never hold on to the result. */
    public Storage storage() {
        return access().storage();
    }

    /** The definition the network backend was opened from (its type and target), or {@code null}. */
    public BackendDefinition definition() {
        NetworkAccess access = access();
        return access.registry().getDefinition(access.backendName());
    }

    /**
     * The plugin's shared {@link RefRegistry} - the same one its PDSections, AccountSections and
     * {@code ECStorage} resolve through, so a {@code Ref} crosses freely between them. Resolved fresh
     * on every call, because a core reload replaces it.
     */
    public RefRegistry refRegistry() {
        return access().refRegistryOf(plugin);
    }

    /**
     * The default codec for {@code type} on the network backend, REF-AWARE: a {@code Ref} field
     * resolves against this plugin's registry once decoded. The plain 1-arg codec on
     * {@link BackendDefinition} is not, which is a trap worth not inheriting here.
     */
    public <V> Codec<V> defaultCodec(Class<V> type) {
        NetworkAccess access = access();
        BackendDefinition definition = access.registry().getDefinition(access.backendName());
        if (definition == null) {
            throw new StorageConfigException("The network backend '" + access.backendName() + "' was"
                    + " registered without a definition, so its default codec cannot be derived.");
        }
        return definition.defaultCodec(type, access.refRegistryOf(plugin));
    }

    /** A raw, uncached repository on the network backend; claims the collection like {@link #manager}. */
    public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
        NetworkAccess access = access();
        claim(access, descriptor);
        return access.storage().repository(descriptor);
    }

    /** {@link #manager(EntityDescriptor, CacheOptions)} with an unbounded cache under the given policy. */
    public <K, V> CachingManager<K, V> manager(EntityDescriptor<K, V> descriptor, CachePolicy policy) {
        return manager(descriptor, CacheOptions.of(policy));
    }

    /**
     * A {@link CachingManager} for {@code descriptor} on the network backend, created in this plugin's
     * shared registry.
     *
     * <p>Deliberately NOT memoized here. A core reload replaces the plugin's registry, and a manager
     * cached in this handle would be one built in the registry that was replaced - the exact failure
     * this facade exists to avoid. The registry memoizes per type anyway, so calling this on every
     * access is correct and cheap.</p>
     */
    public <K, V> CachingManager<K, V> manager(EntityDescriptor<K, V> descriptor, CacheOptions options) {
        NetworkAccess access = access();
        claim(access, descriptor);
        registeredTypes.add(descriptor.type());
        return access.refRegistryOf(plugin).manager(descriptor, access.storage(), options);
    }

    /**
     * Gives back what this handle registered: its entity types leave the plugin's registry and its
     * collection claims are released. Call it from {@code onDisable}.
     *
     * <p>Not a convenience. The core's own cleanup runs off the plugin's PDSection/AccountSection
     * registrations, so a plugin that uses ONLY this facade is never swept: without this call it leaks
     * its type registrations and, through them, keeps its classloader alive. Releasing the claims
     * matters too - otherwise a plugin that is disabled and not re-enabled leaves its collection names
     * locked against everyone else.</p>
     *
     * <p>Only this handle's own registrations are touched, never the shared registry wholesale - the
     * plugin's PDSections keep resolving. Idempotent.</p>
     */
    public void release() {
        NetworkAccess access = accessOrNull();
        if (access != null) {
            RefRegistry registry = access.refRegistryOf(plugin);
            if (registry != null) {
                for (Class<?> type : registeredTypes) {
                    registry.unregister(type);
                }
            }
            for (String collection : claimedCollections) {
                access.registry().releaseCollection(access.backendName(), collection);
            }
        }
        registeredTypes.clear();
        claimedCollections.clear();
    }

    private void claim(NetworkAccess access, EntityDescriptor<?, ?> descriptor) {
        String collection = descriptor.collection();
        if (!access.registry().claimCollection(access.backendName(), collection, owner, descriptor)) {
            throw new StorageConfigException("Plugin '" + plugin.getMetaInfo().getName() + "' wants"
                    + " collection '" + collection + "' on the network backend '" + access.backendName()
                    + "', but it is already used by '"
                    + access.registry().getCollectionOwner(access.backendName(), collection) + "'!");
        }
        claimedCollections.add(collection);
    }

    private static NetworkAccess access() {
        NetworkAccess access = accessOrNull();
        if (access == null) {
            //no failure list: nothing failed to connect, there is simply no storage layer up yet
            throw new StorageUnavailableException("The network storage is not available yet: it comes up"
                    + " with the EverNifeCore storage boot. Reach for it from your plugin's enable, after"
                    + " EverNifeCore has loaded - never from a static initializer.",
                    Collections.<StorageInitFailure>emptyList(), Collections.<String, List<String>>emptyMap(), null);
        }
        return access;
    }

    private static NetworkAccess accessOrNull() {
        Supplier<NetworkAccess> provider = accessProvider;
        return provider == null ? null : provider.get();
    }

    /**
     * What the facade needs from the live PlayerController, so the storage layer never has to import
     * the playerdata one. Wired at bootstrap, resolved fresh on every call.
     */
    public interface NetworkAccess {

        StorageRegistry registry();

        String backendName();

        RefRegistry refRegistryOf(ECPluginData plugin);

        default Storage storage() {
            return registry().get(backendName());
        }
    }
}
