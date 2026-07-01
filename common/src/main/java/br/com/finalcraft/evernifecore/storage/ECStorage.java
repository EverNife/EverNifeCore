package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;

/**
 * Public storage facade for plugins: any plugin that depends on EverNifeCore
 * can persist its own entities in the admin-configured backends, using the same
 * EveryDatabase API that PlayerData uses.
 *
 * <pre>{@code
 * Repository<UUID, Shop> repo = ECStorage.repository(SHOPS_DESCRIPTOR);          // default backend
 * Repository<UUID, Shop> repo = ECStorage.repository("mysql_economy", SHOPS_DESCRIPTOR);
 * }</pre>
 *
 * <p>Collection names are CLAIMED per backend: EveryDatabase caches repositories
 * by collection name, so an accidental reuse across plugins would silently share
 * the first repository - here that becomes a hard error. Convention: prefix your
 * collections with your plugin name ({@code myplugin_...}).</p>
 */
public final class ECStorage {

    private static volatile StorageRegistry registry;

    private ECStorage() {
    }

    /** Wired by the PlayerController bootstrap; safe to swap (volatile). */
    public static void initialize(StorageRegistry storageRegistry) {
        registry = storageRegistry;
    }

    public static Storage backend(String name) {
        return registry().get(name);
    }

    public static Storage defaultBackend() {
        return registry().getDefaultBackend();
    }

    public static <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
        return repository(registry().getDefaultBackendName(), descriptor);
    }

    public static <K, V> Repository<K, V> repository(String backendName, EntityDescriptor<K, V> descriptor) {
        StorageRegistry registry = registry();
        Storage storage = registry.get(backendName);

        String owner = "ECStorage:" + descriptor.type().getName();
        if (!registry.claimCollection(backendName, descriptor.collection(), owner)) {
            throw new StorageConfigException("Collection '" + descriptor.collection()
                    + "' on backend '" + backendName + "' is already used by '"
                    + registry.getCollectionOwner(backendName, descriptor.collection())
                    + "'! Prefix your collections with your plugin name to avoid clashes.");
        }
        return storage.repository(descriptor);
    }

    private static StorageRegistry registry() {
        StorageRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("ECStorage is not initialized yet! It becomes available"
                    + " after EverNifeCore's storage bootstrap (onLoadPre). If you are in onLoad,"
                    + " move the storage access to onEnable.");
        }
        return current;
    }
}
