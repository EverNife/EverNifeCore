package br.com.finalcraft.evernifecore.region.storage;

import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Internal: the {@code regions} collection of one owner's {@link ECStorage}, one entity per region keyed by name.
 * The manager of that owner is its only writer, so the entity carries no version.
 */
public final class RegionStorage {

    public static final String COLLECTION = "regions";

    private final CachingManager<String, RegionRecord> manager;

    public RegionStorage(ECStorage storage) {
        EntityDescriptor<String, RegionRecord> descriptor = EntityDescriptor.builder(String.class, RegionRecord.class)
                .collection(COLLECTION)
                .keyExtractor(record -> record.name)
                .codec(storage.defaultCodec(RegionRecord.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
    }

    /** Every stored record, read from the backend. */
    public CompletableFuture<List<RegionRecord>> loadAll() {
        return manager.repository().all().thenApply(stream -> stream.collect(Collectors.toList()));
    }

    public CompletableFuture<Void> save(RegionRecord record) {
        return manager.saveAndCache(record);
    }

    public CompletableFuture<Boolean> delete(String name) {
        return manager.deleteAndEvict(name);
    }
}
