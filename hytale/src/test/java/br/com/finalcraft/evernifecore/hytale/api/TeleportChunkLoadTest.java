package br.com.finalcraft.evernifecore.hytale.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A teleport into a chunk that is not in memory drops the player out of the world, so the target chunk is
 * loaded first: for free when it already is, after the load otherwise - chained on it, never blocking a world
 * thread on another world's load.
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

        assertTrue(HytaleFPlayer.chunkLoaded(chunks, CHUNK_INDEX).isDone(), "the teleport is still waiting on a load");
        assertEquals(0, chunks.loadRequests, "a chunk already in memory went through the asynchronous load");
    }

    @Test
    void aChunkNotInMemoryIsLoadedAsTickingAndTheTeleportWaitsForItWithoutBlocking() {
        FakeChunkGrid chunks = new FakeChunkGrid(false);

        CompletableFuture<?> loaded = HytaleFPlayer.chunkLoaded(chunks, CHUNK_INDEX);

        assertFalse(loaded.isDone(), "the teleport went ahead before the chunk finished loading");
        assertEquals(1, chunks.loadRequests);
        assertEquals(4, chunks.requestedFlags, "the chunk has to come back ticking, not merely in memory");
        chunks.load.complete(new Ref<>(null));
        assertTrue(loaded.isDone());
    }
}
