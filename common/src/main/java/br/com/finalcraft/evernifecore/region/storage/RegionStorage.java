package br.com.finalcraft.evernifecore.region.storage;

import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import br.com.finalcraft.everydatabase.query.Cursor;
import br.com.finalcraft.everydatabase.query.ScanRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Internal: the {@code regions} collection of one owner's {@link ECStorage}, one entity per region keyed by name.
 * The manager of that owner is its only writer, so the entity carries no version.
 */
public final class RegionStorage {

    public static final String COLLECTION = "regions";

    private static final int SCAN_PAGE = 500;

    private final CachingManager<String, RegionRecord> manager;

    public RegionStorage(ECStorage storage) {
        EntityDescriptor<String, RegionRecord> descriptor = EntityDescriptor.builder(String.class, RegionRecord.class)
                .collection(COLLECTION)
                .keyExtractor(record -> record.name)
                .codec(storage.defaultCodec(RegionRecord.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
    }

    /**
     * Every stored record, read from the backend page by page. A record the backend could not decode comes back as a
     * failed row with its key, so one unreadable region never fails the read of the others.
     */
    public CompletableFuture<List<ScanRow<RegionRecord>>> loadAll() {
        List<ScanRow<RegionRecord>> rows = new ArrayList<>();
        return scanFrom(Cursor.scan(), rows).thenApply(done -> rows);
    }

    private CompletableFuture<Void> scanFrom(Cursor cursor, List<ScanRow<RegionRecord>> rows) {
        return manager.repository().scanAll(cursor, SCAN_PAGE).thenCompose(page -> {
            rows.addAll(page.content());
            Cursor next = page.hasNext() ? page.nextCursor().orElse(null) : null;
            return next == null ? CompletableFuture.completedFuture(null) : scanFrom(next, rows);
        });
    }

    public CompletableFuture<Void> save(RegionRecord record) {
        return manager.saveAndCache(record);
    }

    public CompletableFuture<Boolean> delete(String name) {
        return manager.deleteAndEvict(name);
    }
}
