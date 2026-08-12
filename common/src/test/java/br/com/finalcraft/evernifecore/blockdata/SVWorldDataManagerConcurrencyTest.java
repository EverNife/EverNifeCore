package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.blockdata.SVWorldDataManager.FlushReport;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two ways a write-back store loses data if it is built wrong: a flush encoding a chunk another thread
 * is still writing into, and a backend that refused a write nobody retried.
 */
@ECoreTest
class SVWorldDataManagerConcurrencyTest extends BlockStoreTestBase {

    private static final String WORLD = "world";
    private static final int WRITERS = 8;
    private static final int BLOCKS_PER_WRITER = 200;
    /** The four chunks every writer thread shares, so each flush snapshot is taken under contention. */
    private static final ChunkPos[] SHARED_CHUNKS = {
            ChunkPos.of(0, 0), ChunkPos.of(1, 0), ChunkPos.of(0, 1), ChunkPos.of(1, 1)
    };

    @Test
    void noWriteIsLostWhileTheFlushKeepsRunning() throws Exception {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_concurrent");
        Map<String, String> written = new ConcurrentHashMap<>();

        ExecutorService threads = Executors.newFixedThreadPool(WRITERS + 1);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicBoolean stillWriting = new AtomicBoolean(true);
        List<Future<?>> writers = new ArrayList<>();
        try {
            for (int writer = 0; writer < WRITERS; writer++) {
                final int id = writer;
                writers.add(threads.submit(() -> {
                    startTogether.await();
                    for (int i = 0; i < BLOCKS_PER_WRITER; i++) {
                        BlockPos pos = blockOf(id, i);
                        String owner = "writer-" + id + "-" + i;
                        store.setBlock(WORLD, pos, new Marker(owner, i)).join();
                        written.put(pos.serialize(), owner);
                    }
                    return null;
                }));
            }
            Future<?> flusher = threads.submit(() -> {
                startTogether.await();
                while (stillWriting.get()) {
                    store.flush().join();
                    Thread.sleep(1L);
                }
                return null;
            });

            startTogether.countDown();
            for (Future<?> writer : writers) {
                writer.get(60L, TimeUnit.SECONDS);
            }
            stillWriting.set(false);
            flusher.get(60L, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }

        store.flush().join();
        store.close();

        SVWorldDataManager<Marker> reopened = storeOn(storage, "blocks_concurrent");
        Map<String, String> persisted = new TreeMap<>();
        for (ChunkPos chunk : SHARED_CHUNKS) {
            for (Map.Entry<BlockPos, Marker> block : reopened.getChunk(WORLD, chunk).join().entrySet()) {
                persisted.put(block.getKey().serialize(), block.getValue().getOwner());
            }
        }

        assertEquals(WRITERS * BLOCKS_PER_WRITER, written.size(), "the reference map itself must be complete");
        assertEquals(new TreeMap<>(written), persisted,
                "a flush snapshots each chunk under its monitor, so no write can fall between the two");
    }

    @Test
    void aFailedBatchLeavesEveryChunkOfItDirtyForTheNextFlush() {
        ScriptedStorage backend = openScriptedStorage();
        SVWorldDataManager<Marker> store = rawStoreOn(backend, "blocks_flush_failure");
        BlockPos here = BlockPos.of(5, 64, 7);
        BlockPos overThere = BlockPos.of(20, 70, 3);
        store.setBlock(WORLD, here, new Marker("alice", 10)).join();
        store.setBlock(WORLD, overThere, new Marker("bob", 20)).join();

        IllegalStateException outage = new IllegalStateException("backend down");
        backend.failNextSaveAll(outage);
        FlushReport refused = store.flush().join();

        assertEquals(0, refused.getSavedChunks());
        assertTrue(refused.hasFailures());
        assertEquals(2, refused.getFailures().size(),
                "the batch answers with one failure for the whole list, so every chunk in it goes back to dirty");
        assertTrue(refused.getFailures().containsKey(WorldChunkData.keyOf(WORLD, here.getChunkPos())));
        assertTrue(refused.getFailures().containsKey(WorldChunkData.keyOf(WORLD, overThere.getChunkPos())));
        for (Throwable cause : refused.getFailures().values()) {
            assertSame(outage, cause, "the report names what the backend answered, not the future's wrapper");
        }

        FlushReport recovered = store.flush().join();

        assertEquals(2, recovered.getSavedChunks(), "the next flush retries what the outage refused");
        assertFalse(recovered.hasFailures());

        SVWorldDataManager<Marker> reopened = rawStoreOn(backend, "blocks_flush_failure");
        assertEquals("alice", reopened.getBlock(WORLD, here).join().getOwner());
        assertEquals("bob", reopened.getBlock(WORLD, overThere).join().getOwner());
    }

    @Test
    void aWriteThatArrivedDuringTheFailedFlushIsNotLost() {
        ScriptedStorage backend = openScriptedStorage();
        SVWorldDataManager<Marker> store = rawStoreOn(backend, "blocks_flush_failure_write");
        BlockPos pos = BlockPos.of(5, 64, 7);
        store.setBlock(WORLD, pos, new Marker("alice", 10)).join();

        backend.failNextSaveAll(new IllegalStateException("backend down"));
        assertTrue(store.flush().join().hasFailures());

        //the chunk is dirty again AND holds the newer value: the failed snapshot never went back into the cache
        store.setBlock(WORLD, pos, new Marker("bob", 20)).join();
        assertEquals(1, store.flush().join().getSavedChunks());

        SVWorldDataManager<Marker> reopened = rawStoreOn(backend, "blocks_flush_failure_write");
        assertEquals("bob", reopened.getBlock(WORLD, pos).join().getOwner());
    }

    /** A block of the shared chunk grid, at a height no other writer or iteration uses. */
    private static BlockPos blockOf(int writer, int iteration) {
        ChunkPos chunk = SHARED_CHUNKS[iteration % SHARED_CHUNKS.length];
        return BlockPos.of(chunk.getXStart() + (writer % 8), writer * 1000 + iteration,
                chunk.getZStart() + (iteration % 16));
    }
}
