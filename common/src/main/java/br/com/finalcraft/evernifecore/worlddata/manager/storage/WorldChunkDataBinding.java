package br.com.finalcraft.evernifecore.worlddata.manager.storage;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;

/**
 * The resolved storage wiring of one {@code SVDataManager<O>}: a String-keyed
 * {@link CachingManager} over the {@link WorldChunkData} entity, on a given {@link Storage} and
 * collection, with a codec that restores the concrete block-value type {@code O}.
 *
 * <p>This is the non-player counterpart of {@code PlayerDataBinding}: it builds the
 * {@link EntityDescriptor} and the {@link CachingManager} through the same public EveryDatabase API,
 * only keyed by the chunk String {@code "<world>/<chunkX>/<chunkZ>"} instead of a player UUID. The
 * cache is unbounded and resident (the whole dataset is loaded at boot, as the region-YAML engine
 * did), so a held chunk stays the canonical live instance and dirty write-back is never
 * time-evicted.</p>
 */
public final class WorldChunkDataBinding {

    private final Storage storage;
    private final EntityDescriptor<String, WorldChunkData> descriptor;
    private final CachingManager<String, WorldChunkData> manager;

    private WorldChunkDataBinding(Storage storage,
                                  EntityDescriptor<String, WorldChunkData> descriptor,
                                  CachingManager<String, WorldChunkData> manager) {
        this.storage = storage;
        this.descriptor = descriptor;
        this.manager = manager;
    }

    /** Builds a resident (unbounded, always-fresh) binding for {@code valueType} on {@code storage}. */
    public static <O> WorldChunkDataBinding create(Class<O> valueType, String collection, Storage storage) {
        return create(valueType, collection, storage, new RefRegistry(), CacheOptions.of(CachePolicy.always()));
    }

    public static <O> WorldChunkDataBinding create(Class<O> valueType, String collection, Storage storage,
                                                   RefRegistry registry, CacheOptions cacheOptions) {
        Codec<WorldChunkData> codec = new WorldChunkDataCodec<>(valueType);
        EntityDescriptor<String, WorldChunkData> descriptor = EntityDescriptor
                .builder(String.class, WorldChunkData.class)
                .collection(collection)
                .keyExtractor(WorldChunkData::getChunkKey)
                .codec(codec)
                .build();
        CachingManager<String, WorldChunkData> manager = registry.manager(descriptor, storage, cacheOptions);
        return new WorldChunkDataBinding(storage, descriptor, manager);
    }

    public Storage getStorage() {
        return storage;
    }

    public EntityDescriptor<String, WorldChunkData> getDescriptor() {
        return descriptor;
    }

    public String getCollection() {
        return descriptor.collection();
    }

    /** The cache + repository façade backing the chunk entities. */
    public CachingManager<String, WorldChunkData> getManager() {
        return manager;
    }

    /** The underlying repository (uncached) - same instance the manager wraps. */
    public Repository<String, WorldChunkData> getRepository() {
        return manager.repository();
    }
}
