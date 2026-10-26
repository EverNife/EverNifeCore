package br.com.finalcraft.evernifecore.group.storage;

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
 * Internal: the {@code groups} collection of one owner's {@link ECStorage}, one entity per group keyed by name. The
 * manager of that owner is its only writer, so the entity carries no version.
 */
public final class GroupStorage {

    public static final String COLLECTION = "groups";

    private static final int SCAN_PAGE = 500;

    private final CachingManager<String, GroupRecord> manager;

    public GroupStorage(ECStorage storage) {
        EntityDescriptor<String, GroupRecord> descriptor = EntityDescriptor.builder(String.class, GroupRecord.class)
                .collection(COLLECTION)
                .keyExtractor(record -> record.name)
                .codec(storage.defaultCodec(GroupRecord.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
    }

    /** Every stored record, page by page; a record the backend could not decode comes back as a failed row with its key. */
    public CompletableFuture<List<ScanRow<GroupRecord>>> loadAll() {
        List<ScanRow<GroupRecord>> rows = new ArrayList<>();
        return scanFrom(Cursor.scan(), rows).thenApply(done -> rows);
    }

    private CompletableFuture<Void> scanFrom(Cursor cursor, List<ScanRow<GroupRecord>> rows) {
        return manager.repository().scanAll(cursor, SCAN_PAGE).thenCompose(page -> {
            rows.addAll(page.content());
            Cursor next = page.hasNext() ? page.nextCursor().orElse(null) : null;
            return next == null ? CompletableFuture.completedFuture(null) : scanFrom(next, rows);
        });
    }

    public CompletableFuture<Void> save(GroupRecord record) {
        return manager.saveAndCache(record);
    }

    public CompletableFuture<Boolean> delete(String name) {
        return manager.deleteAndEvict(name);
    }
}
