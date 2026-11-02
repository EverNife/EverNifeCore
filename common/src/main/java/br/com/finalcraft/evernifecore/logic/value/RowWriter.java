package br.com.finalcraft.evernifecore.logic.value;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Writes the rows of one collection to its backend one write per key at a time, each write a copy of the row's latest
 * state taken under the row's monitor, so the backend ends with what memory holds whatever order writes complete in.
 * A change made while its key is being written only marks the key: at most one more write follows, however many
 * changes came. Nothing here holds a row's monitor while the backend writes.
 */
final class RowWriter<R> {

    private final Function<R, R> copy;
    private final Function<R, CompletableFuture<?>> save;
    private final Consumer<Throwable> onFailure;
    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();

    private final class Lane {
        R row;
        boolean dirty;
        final CompletableFuture<Void> drained = new CompletableFuture<>();

        Lane(R row) {
            this.row = row;
        }
    }

    /**
     * @param copy      a snapshot of a row the backend may serialize on its own thread; called under the row's monitor
     * @param save      starts the write of a snapshot
     * @param onFailure told of a write that failed; the next change of that key writes again
     */
    RowWriter(Function<R, R> copy, Function<R, CompletableFuture<?>> save, Consumer<Throwable> onFailure) {
        this.copy = copy;
        this.save = save;
        this.onFailure = onFailure;
    }

    /** Writes {@code row}'s current state under {@code key}, now or once the write of that key on its way lands. */
    void write(String key, R row) {
        AtomicReference<Lane> started = new AtomicReference<>();
        lanes.compute(key, (k, lane) -> {
            if (lane != null) {
                lane.row = row;
                lane.dirty = true;
                return lane;
            }
            started.set(new Lane(row));
            return started.get();
        });
        if (started.get() != null) {
            pump(key, started.get());
        }
    }

    private void pump(String key, Lane lane) {
        Object[] latest = new Object[1];
        lanes.compute(key, (k, same) -> {
            lane.dirty = false;
            latest[0] = lane.row;
            return same;
        });
        @SuppressWarnings("unchecked") R row = (R) latest[0];
        R snapshot;
        synchronized (row) {
            snapshot = copy.apply(row);
        }
        CompletableFuture<?> write;
        try {
            write = save.apply(snapshot);
        } catch (RuntimeException thrown) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(thrown);
            write = failed;
        }
        write.whenComplete((ok, error) -> {
            if (error != null) {
                onFailure.accept(error);
            }
            boolean[] again = {false};
            lanes.compute(key, (k, same) -> {
                again[0] = lane.dirty;
                return again[0] ? same : null;
            });
            if (again[0]) {
                pump(key, lane);
            } else {
                lane.drained.complete(null);
            }
        });
    }

    /** Completes once every key written so far has landed (or failed). */
    CompletableFuture<Void> drained() {
        return CompletableFuture.allOf(lanes.values().stream().map(lane -> lane.drained).toArray(CompletableFuture[]::new));
    }
}
