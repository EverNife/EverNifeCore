package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.PlayerDataAdminConfig;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The resolved storage wiring of the PlayerData base entity:
 * backend = {@code playerdata.storage-backend-id ?? default-backend}, collection from
 * {@code playerdata.collection}, codec according to the backend. The indices ({@code name},
 * {@code lastSeen}) and the optimistic lock come from the PlayerData annotations.
 *
 * <p>The base entity is cached through a {@link CachingManager} created in the
 * {@code ECRegistries.global()} registry with an always(), unbounded policy: the cached set ==
 * the loaded set, and a write-back ({@link PlayerData} is {@code IDirtyable}) dirty cell is never
 * time-evicted, so a held reference stays the canonical live instance.</p>
 */
@Getter
public final class PlayerDataBinding {

    public static final String CLAIM_OWNER = "EverNifeCore:PlayerData";

    private final String backendName;
    private final Storage storage;
    private final EntityDescriptor<UUID, PlayerData> descriptor;
    /** The cache + repository façade backing the base entity (in {@code ECRegistries.global()}). */
    private final CachingManager<UUID, PlayerData> manager;
    /** Soft warnings the bind-guard raised (e.g. versioned base on a non-enforcing shared backend). */
    private final List<String> resolutionWarnings;

    private PlayerDataBinding(String backendName, Storage storage,
                              EntityDescriptor<UUID, PlayerData> descriptor,
                              CachingManager<UUID, PlayerData> manager,
                              List<String> resolutionWarnings) {
        this.backendName = backendName;
        this.storage = storage;
        this.descriptor = descriptor;
        this.manager = manager;
        this.resolutionWarnings = Collections.unmodifiableList(resolutionWarnings);
    }

    public static PlayerDataBinding resolve(ParsedStorageConfig parsed, StorageRegistry registry,
                                            RefRegistry globalRegistry) {
        PlayerDataAdminConfig adminConfig = parsed.getPlayerData();
        String backendName = adminConfig.getBackendName() != null
                ? adminConfig.getBackendName()
                : parsed.getDefaultBackendName();

        //The parser already fatally validated 'playerdata.storage-backend-id' and 'default-backend' (enabled)
        BackendDefinition backend = parsed.getBackend(backendName).get();
        Storage storage = registry.get(backendName);

        String collection = adminConfig.getCollection();
        if (!registry.claimCollection(backendName, collection, CLAIM_OWNER)) {
            throw new StorageConfigException("PlayerData wants collection '" + collection
                    + "' on backend '" + backendName + "', but it is already used by '"
                    + registry.getCollectionOwner(backendName, collection) + "'!");
        }

        Codec<PlayerData> codec = EntitySchemaMigratingCodec.wrap(PlayerData.class,
                BindingResolver.defaultCodec(backend, PlayerData.class), "uuid");
        EntityDescriptor<UUID, PlayerData> descriptor = EntityDescriptor
                .builder(UUID.class, PlayerData.class)
                .collection(collection)
                .keyExtractor(PlayerData::getUniqueId)
                .codec(codec)
                .build();   //@Indexed name/lastSeen + @OptimisticLock are scanned here

        //Soft-warn (never abort): the versioned base entity on a lock-unenforcing backend with
        //multi-instance intent (an enabled redis block) is a last-write-wins risk. The caller logs
        //these warnings.
        List<String> warnings = new ArrayList<>();
        PdSyncBindGuard.check("PlayerData (base entity)", descriptor, storage, parsed, false, warnings);

        //replacement semantics: on a core reload the previous base manager is still registered in the
        //(identity-stable) global registry; the reload tears it down centrally, post-swap
        CachingManager<UUID, PlayerData> manager = globalRegistry.managerReplacing(descriptor, storage,
                CachePolicy.always(), retired -> {});
        return new PlayerDataBinding(backendName, storage, descriptor, manager, warnings);
    }

    /**
     * Rebinds the PlayerData base entity to another backend (already validated and enabled) -
     * the runtime transfer cutover. Same collection; storage, repository and default codec
     * follow the target backend.
     */
    public static PlayerDataBinding rebindTo(PlayerDataBinding current, String targetBackendName,
                                             ParsedStorageConfig parsed, StorageRegistry registry,
                                             RefRegistry globalRegistry) {
        BackendDefinition backend = parsed.getBackend(targetBackendName)
                .orElseThrow(() -> new StorageConfigException("Backend '" + targetBackendName + "' is not declared!"));
        Storage storage = registry.get(targetBackendName);
        String collection = current.getCollection();

        if (!registry.claimCollection(targetBackendName, collection, CLAIM_OWNER)) {
            throw new StorageConfigException("Cannot transfer PlayerData: collection '" + collection
                    + "' on backend '" + targetBackendName + "' is already used by '"
                    + registry.getCollectionOwner(targetBackendName, collection) + "'!");
        }

        Codec<PlayerData> codec = EntitySchemaMigratingCodec.wrap(PlayerData.class,
                BindingResolver.defaultCodec(backend, PlayerData.class), "uuid");
        EntityDescriptor<UUID, PlayerData> descriptor = EntityDescriptor
                .builder(UUID.class, PlayerData.class)
                .collection(collection)
                .keyExtractor(PlayerData::getUniqueId)
                .codec(codec)
                .build();
        //same soft-warn the boot-time resolve applies: a runtime transfer onto a lock-unenforcing
        //backend under multi-instance intent is surfaced (not blocked)
        List<String> warnings = new ArrayList<>();
        PdSyncBindGuard.check("PlayerData (transfer target)", descriptor, storage, parsed, false, warnings);
        //replacement semantics, like the section transfer path: the pre-transfer manager keeps serving
        //until this swap, and the transfer service owns its teardown (frozen, restored on failure)
        CachingManager<UUID, PlayerData> manager = globalRegistry.managerReplacing(descriptor, storage,
                CachePolicy.always(), retired -> {});
        return new PlayerDataBinding(targetBackendName, storage, descriptor, manager, warnings);
    }

    public String getCollection() {
        return descriptor.collection();
    }

    /** The underlying repository (uncached) - same instance the manager wraps. */
    public Repository<UUID, PlayerData> getRepository() {
        return manager.repository();
    }
}
