package br.com.finalcraft.evernifecore.quest;

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
 * Internal: the quest states of owners that are not players (groups, the server, regions, worlds) in one plugin's
 * {@link ECStorage}, collection {@value #COLLECTION}, one entity per owner and quest keyed
 * {@code <ownerKind>:<ownerId>|<questId>}. The plugin's engine is its only writer.
 */
public final class QuestStorage {

    public static final String COLLECTION = "quest_states";

    private static final int SCAN_PAGE = 500;

    private final CachingManager<String, Record> manager;

    QuestStorage(ECStorage storage) {
        EntityDescriptor<String, Record> descriptor = EntityDescriptor.builder(String.class, Record.class)
                .collection(COLLECTION)
                .keyExtractor(record -> record.key)
                .codec(storage.defaultCodec(Record.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
    }

    static String keyOf(String owner, String questId) {
        return owner + "|" + questId;
    }

    CompletableFuture<List<ScanRow<Record>>> loadAll() {
        List<ScanRow<Record>> rows = new ArrayList<>();
        return scanFrom(Cursor.scan(), rows).thenApply(done -> rows);
    }

    private CompletableFuture<Void> scanFrom(Cursor cursor, List<ScanRow<Record>> rows) {
        return manager.repository().scanAll(cursor, SCAN_PAGE).thenCompose(page -> {
            rows.addAll(page.content());
            Cursor next = page.hasNext() ? page.nextCursor().orElse(null) : null;
            return next == null ? CompletableFuture.completedFuture(null) : scanFrom(next, rows);
        });
    }

    CompletableFuture<Void> save(Record record) {
        return manager.saveAndCache(record);
    }

    CompletableFuture<Boolean> delete(String key) {
        return manager.deleteAndEvict(key);
    }

    /** Internal: one stored state. */
    public static final class Record {
        public String key;
        /** {@code ProgressOwner.toString()}. */
        public String owner;
        public QuestState state;

        public Record() {
            //Jackson
        }

        Record(String owner, QuestState state) {
            this.key = keyOf(owner, state.getQuestId());
            this.owner = owner;
            this.state = state;
        }
    }
}
