package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    // ---------------------------------------------------------------------
    // Plugin-owned inline backends (a plugin routing its own data elsewhere)
    // ---------------------------------------------------------------------

    /**
     * Opens a NEW, plugin-OWNED {@link Storage} from an inline single-backend section - the shape
     * {@link StorageYamlParser#parseInlineBackend} reads, where the SINGLE child key IS the backend
     * type. The storage is created AND initialized (connected) before returning; a backend that cannot
     * be reached throws.
     *
     * <p>Unlike {@link #backend(String)} / {@link #defaultBackend()}, this does NOT touch the core's
     * shared registry: the returned storage is YOURS to keep and to {@link Storage#close() close} when
     * your plugin disables. Every backend's runtime driver is already downloaded by the core at boot, so
     * whichever type the admin picks in that section just works.</p>
     *
     * <p>This is the "store a pointer in PlayerData, the volume elsewhere" pattern: keep the small
     * per-player index in a PDSection (the core's storage.yml routing) and route the fat payload through
     * a backend the plugin's own config declares.</p>
     *
     * <pre>{@code
     * // onEnable (your plugin depends on EverNifeCore, so the drivers are already loaded):
     * Storage storage = ECStorage.openBackend(config.getConfigSection("storage"));
     * Repository<UUID, Snapshot> repo = storage.repository(MY_DESCRIPTOR);
     * // onDisable:
     * storage.close().join();
     * }</pre>
     */
    public static Storage openBackend(ConfigSection section) {
        return openBackend(section, StorageLogConfig.defaults());
    }

    /** As {@link #openBackend(ConfigSection)}, with an explicit {@link StorageLogConfig}. */
    public static Storage openBackend(ConfigSection section, StorageLogConfig logConfig) {
        List<String> warnings = new ArrayList<>();
        BackendDefinition backend = StorageYamlParser.parseInlineBackend(section, warnings);
        for (String warning : warnings) {
            logWarning(warning);
        }
        Storage storage = backend.createStorage(logConfig);
        storage.init().exceptionally(error -> {
            throw new StorageConfigException("Failed to open the '" + backend.getType().getId()
                    + "' storage backend declared at '" + section.getPath() + "'!", error);
        }).join();
        return storage;
    }

    private static void logWarning(String message) {
        try {
            EverNifeCore.getLog().warning(message);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData/log configured): falls back to JUL
            Logger.getLogger("EverNifeCore").log(Level.WARNING, message);
        }
    }
}
