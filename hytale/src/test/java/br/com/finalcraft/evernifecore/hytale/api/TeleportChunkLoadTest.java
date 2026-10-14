package br.com.finalcraft.evernifecore.hytale.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A teleport into a chunk that is not in memory drops the player out of the world, so the target chunk is
 * loaded first: for free when it already is, by waiting for the load otherwise - but never by waiting on
 * the target world's own thread, which is the thread that finishes the load.
 */
class TeleportChunkLoadTest {

    private static final long CHUNK_INDEX = 42L;

    /** A chunk grid with one chunk that is either in memory or still to be loaded, counting load requests. */
    private static class FakeChunkGrid extends ChunkGrid {
        private final Ref<ChunkStore> inMemory;
        private final CompletableFuture<Ref<ChunkStore>> load = new CompletableFuture<>();
        private int loadRequests;
        private int requestedFlags;

        FakeChunkGrid(boolean chunkInMemory) {
            super(null);
            this.inMemory = chunkInMemory ? new Ref<>(null) : null;
        }

        @Override
        public Ref<ChunkStore> getChunkReference(long index) {
            assertEquals(CHUNK_INDEX, index);
            return inMemory;
        }

        @Override
        public CompletableFuture<Ref<ChunkStore>> getChunkReferenceAsync(long index, int flags) {
            assertEquals(CHUNK_INDEX, index);
            loadRequests++;
            requestedFlags = flags;
            return load;
        }
    }

    @Test
    void aChunkAlreadyInMemoryIsNotLoadedAgain() {
        FakeChunkGrid chunks = new FakeChunkGrid(true);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> HytaleFPlayer.awaitChunkLoaded(chunks, false, CHUNK_INDEX),
                "the teleport is still waiting on a load");

        assertEquals(0, chunks.loadRequests, "a chunk already in memory went through the asynchronous load");
    }

    @Test
    void aChunkNotInMemoryIsLoadedAsTickingAndWaitedFor() throws Exception {
        FakeChunkGrid chunks = new FakeChunkGrid(false);
        ForkJoinPool.commonPool().execute(() -> {
            sleepQuietly(100);
            chunks.load.complete(new Ref<>(null));
        });

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> HytaleFPlayer.awaitChunkLoaded(chunks, false, CHUNK_INDEX),
                "the teleport is still waiting on a load");

        assertTrue(chunks.load.isDone(), "the teleport went ahead before the chunk finished loading");
        assertEquals(1, chunks.loadRequests);
        assertEquals(4, chunks.requestedFlags, "the chunk has to come back ticking, not merely in memory");
    }

    @Test
    void theWorldThreadStartsTheLoadWithoutWaitingOnItself() {
        FakeChunkGrid chunks = new FakeChunkGrid(false);

        CompletableFuture<Void> call = CompletableFuture.runAsync(() -> HytaleFPlayer.awaitChunkLoaded(chunks, true, CHUNK_INDEX));

        assertTrue(waitFor(call), "the world thread blocked on a load only it can finish");
        assertEquals(1, chunks.loadRequests, "the load was not even started");
        assertFalse(chunks.load.isDone());
    }

    private static boolean waitFor(CompletableFuture<Void> call) {
        try {
            call.get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
