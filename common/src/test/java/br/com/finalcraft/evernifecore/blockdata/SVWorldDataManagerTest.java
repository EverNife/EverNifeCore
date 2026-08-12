package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.blockdata.SVWorldDataManager.FlushReport;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.Repository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The write-back cycle of the block store: a block written into the cached chunk, persisted by a flush and
 * read back by a manager whose cache is cold; a replace announced once and saved in place; a chunk that lost
 * its last block dropped from the backend; and the periodic flush that stops when the store closes.
 */
@ECoreTest
class SVWorldDataManagerTest extends BlockStoreTestBase {

    private static final String WORLD = "world";

    // -----------------------------------------------------------------------------------------------------
    //  Round trip
    // -----------------------------------------------------------------------------------------------------

    @Test
    void blocksRoundTripThroughTheInMemoryBackend() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_memory");

        store.setBlock(WORLD, BlockPos.of(5, 64, 7), new Marker("alice", 10)).join();
        store.setBlock(WORLD, BlockPos.of(20, 70, 3), new Marker("bob", 20)).join();

        FlushReport report = store.flush().join();
        assertEquals(2, report.getSavedChunks(), "the two blocks sit in two different chunks");
        assertEquals(0, report.getDeletedChunks());
        assertFalse(report.hasFailures());

        store.close();
        SVWorldDataManager<Marker> reopened = storeOn(storage, "blocks_memory");

        Marker alice = reopened.getBlock(WORLD, BlockPos.of(5, 64, 7)).join();
        assertNotNull(alice);
        assertEquals("alice", alice.getOwner());
        assertEquals(10, alice.getAmount());
        assertEquals("bob", reopened.getBlock(WORLD, BlockPos.of(20, 70, 3)).join().getOwner());
        assertNull(reopened.getBlock(WORLD, BlockPos.of(1, 1, 1)).join(), "an unwritten block answers null");
    }

    @Test
    void blocksRoundTripThroughTheH2Backend() {
        String url = "jdbc:h2:mem:blockdata_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        BlockPos pos = BlockPos.of(100, 65, -50);

        SVWorldDataManager<Marker> store = storeOn(openStorage(BackendDefinition.h2(url)), "blocks_h2");
        store.setBlock(WORLD, pos, new Marker("carol", 99)).join();
        store.close();

        //a second handle on the same database: what comes back was read off H2, not off a shared cache
        SVWorldDataManager<Marker> reopened = storeOn(openStorage(BackendDefinition.h2(url)), "blocks_h2");
        Marker carol = reopened.getBlock(WORLD, pos).join();
        assertNotNull(carol);
        assertEquals("carol", carol.getOwner());
        assertEquals(99, carol.getAmount());
    }

    @Test
    void aWorldNameCarryingSlashesSurvivesTheRoundTrip() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_nested_world");
        BlockPos pos = BlockPos.of(5, 64, 7);

        store.setBlock("nether/deep", pos, new Marker("alice", 10)).join();
        store.flush().join();
        store.close();

        SVWorldDataManager<Marker> reopened = storeOn(storage, "blocks_nested_world");
        assertEquals("alice", reopened.getBlock("nether/deep", pos).join().getOwner());
        assertNull(reopened.getBlock("nether", pos).join(), "'nether/deep' is one world, not a world plus a path");
    }

    @Test
    void theWorldBlockPosOverloadsAddressTheSameBlock() {
        SVWorldDataManager<Marker> store = storeOn(openStorage(BackendDefinition.memory()), "blocks_overloads");
        WorldBlockPos pos = BlockPos.of(5, 64, 7).atWorld(WORLD);

        store.setBlock(pos, new Marker("alice", 10)).join();

        assertEquals("alice", store.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getOwner());
        assertEquals("alice", store.getBlock(pos).join().getOwner());
        assertEquals("alice", store.peekBlock(pos).getOwner());

        store.removeBlock(pos).join();
        assertNull(store.getBlock(pos).join());
    }

    // -----------------------------------------------------------------------------------------------------
    //  Change notification
    // -----------------------------------------------------------------------------------------------------

    @Test
    void replacingABlockNotifiesOnceAndSavesTheChunkInPlace() {
        ScriptedStorage backend = openScriptedStorage();
        List<String> changes = new CopyOnWriteArrayList<>();
        SVWorldDataManager<Marker> store = register(SVWorldDataManager.targeting(Marker.class)
                .on(backend, "blocks_replace")
                .autoFlushEvery(Duration.ZERO)
                .onChange((world, pos, oldValue, newValue) ->
                        changes.add(world + " " + pos.serialize() + " " + nameOf(oldValue) + "->" + nameOf(newValue)))
                .build().join());

        BlockPos pos = BlockPos.of(5, 64, 7);
        store.setBlock(WORLD, pos, new Marker("alice", 10)).join();
        store.flush().join();
        backend.resetCalls();

        store.setBlock(WORLD, pos, new Marker("bob", 20)).join();

        assertEquals(2, changes.size(), "one notification for the write, one for the replace");
        assertEquals("world 5|64|7 none->alice", changes.get(0));
        assertEquals("world 5|64|7 alice->bob", changes.get(1), "a replace carries both sides in ONE call");

        FlushReport report = store.flush().join();
        assertEquals(1, report.getSavedChunks());
        assertEquals(0, report.getDeletedChunks());
        assertEquals(1, backend.callsTo("saveAll"));
        assertEquals(0, backend.callsTo("delete"), "a replace never deletes and re-creates the chunk");
    }

    @Test
    void computeBlockAppliesTheMutatorAndRemovesOnNull() {
        SVWorldDataManager<Marker> store = storeOn(openStorage(BackendDefinition.memory()), "blocks_compute");
        BlockPos pos = BlockPos.of(5, 64, 7);
        List<String> changes = new CopyOnWriteArrayList<>();
        store.setBlock(WORLD, pos, new Marker("alice", 10)).join();

        Marker raised = store.computeBlock(WORLD, pos, current -> {
            current.setAmount(current.getAmount() + 5);
            return current;
        }).join();

        assertEquals(15, raised.getAmount());
        assertEquals(15, store.getBlock(WORLD, pos).join().getAmount());
        assertNull(store.computeBlock(WORLD, pos, current -> null).join(), "returning null removes the block");
        assertNull(store.getBlock(WORLD, pos).join());
        assertTrue(changes.isEmpty(), "no listener was registered on this store");
    }

    // -----------------------------------------------------------------------------------------------------
    //  Emptied chunks
    // -----------------------------------------------------------------------------------------------------

    @Test
    void removingTheLastBlockDeletesTheChunkEntity() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_remove");
        BlockPos alone = BlockPos.of(5, 64, 7);
        Repository<String, WorldChunkData<Marker>> repository = store.manager().repository();
        String chunkKey = WorldChunkData.keyOf(WORLD, alone.getChunkPos());

        store.setBlock(WORLD, alone, new Marker("alice", 10)).join();
        store.flush().join();
        assertTrue(repository.exists(chunkKey).join());

        store.removeBlock(WORLD, alone).join();
        FlushReport report = store.flush().join();

        assertEquals(1, report.getDeletedChunks());
        assertEquals(0, report.getSavedChunks());
        assertFalse(repository.exists(chunkKey).join(), "an emptied chunk leaves no entity behind");
        assertTrue(repository.find(WorldChunkData.META_KEY).join().isPresent(),
                "the grid sentinel holds no blocks and must survive the sweep that deletes empty chunks");
        assertNull(store.getBlock(WORLD, alone).join());
    }

    @Test
    void aChunkRefilledAfterTheDeleteIsPersistedByTheNextFlush() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_refill");
        BlockPos pos = BlockPos.of(5, 64, 7);

        store.setBlock(WORLD, pos, new Marker("alice", 10)).join();
        store.flush().join();
        store.removeBlock(WORLD, pos).join();
        assertEquals(1, store.flush().join().getDeletedChunks());

        store.setBlock(WORLD, pos, new Marker("bob", 20)).join();
        assertEquals(1, store.flush().join().getSavedChunks());

        store.close();
        SVWorldDataManager<Marker> reopened = storeOn(storage, "blocks_refill");
        assertEquals("bob", reopened.getBlock(WORLD, pos).join().getOwner());
    }

    // -----------------------------------------------------------------------------------------------------
    //  Peek
    // -----------------------------------------------------------------------------------------------------

    @Test
    void peekServesTheCacheAndNeverReadsTheBackend() {
        ScriptedStorage backend = openScriptedStorage();
        SVWorldDataManager<Marker> store = rawStoreOn(backend, "blocks_peek");
        BlockPos pos = BlockPos.of(5, 64, 7);

        store.setBlock(WORLD, pos, new Marker("alice", 10)).join();
        store.flush().join();
        assertEquals("alice", store.peekBlock(WORLD, pos).getOwner());

        store.manager().evict(WorldChunkData.keyOf(WORLD, pos.getChunkPos()));
        backend.resetCalls();

        assertNull(store.peekBlock(WORLD, pos), "an evicted chunk is absent as far as a peek is concerned");
        assertEquals(0, backend.callsTo("find"));
        assertEquals(0, backend.callsTo("findMany"));

        assertEquals("alice", store.getBlock(WORLD, pos).join().getOwner(), "the loading read still finds it");
        assertTrue(backend.callsTo("find") > 0, "and that one DID go to the backend");
    }

    @Test
    void getChunkAnswersAnImmutableSnapshotOfTheWholeChunk() {
        SVWorldDataManager<Marker> store = storeOn(openStorage(BackendDefinition.memory()), "blocks_chunk");
        BlockPos first = BlockPos.of(5, 64, 7);
        BlockPos second = BlockPos.of(8, 64, 9);
        store.setBlock(WORLD, first, new Marker("alice", 10)).join();
        store.setBlock(WORLD, second, new Marker("dave", 30)).join();

        Map<BlockPos, Marker> chunk = store.getChunk(WORLD, first.getChunkPos()).join();

        assertEquals(2, chunk.size());
        assertEquals("alice", chunk.get(first).getOwner());
        assertEquals("dave", chunk.get(second).getOwner());
        assertTrue(store.getChunk(WORLD, BlockPos.of(500, 64, 500).getChunkPos()).join().isEmpty(),
                "a chunk holding nothing answers empty and is not created");
    }

    // -----------------------------------------------------------------------------------------------------
    //  Periodic flush and close
    // -----------------------------------------------------------------------------------------------------

    @Test
    void thePeriodicFlushPersistsOnItsOwnAndCloseStopsIt() throws Exception {
        ScriptedStorage backend = openScriptedStorage();
        SVWorldDataManager<Marker> store = register(SVWorldDataManager.targeting(Marker.class)
                .on(backend, "blocks_tick")
                .autoFlushEvery(Duration.ofMillis(200))
                .build().join());

        store.setBlock(WORLD, BlockPos.of(5, 64, 7), new Marker("alice", 10)).join();
        waitUntil("the tick to persist the first write", () -> backend.callsTo("saveAll") >= 1);

        store.setBlock(WORLD, BlockPos.of(300, 64, 300), new Marker("bob", 20)).join();
        store.close();
        int savesAtClose = backend.callsTo("saveAll");

        Thread.sleep(700);
        assertEquals(savesAtClose, backend.callsTo("saveAll"), "close() stops the tick, it does not just skip one");

        SVWorldDataManager<Marker> reopened = rawStoreOn(backend, "blocks_tick");
        assertEquals("alice", reopened.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getOwner());
        assertEquals("bob", reopened.getBlock(WORLD, BlockPos.of(300, 64, 300)).join().getOwner(),
                "the write that the tick had not reached yet went out with the final flush");
    }

    @Test
    void closeIsIdempotentAndReleasesTheEntityForARebuild() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_close_twice");
        store.setBlock(WORLD, BlockPos.of(5, 64, 7), new Marker("alice", 10)).join();

        store.close();
        store.close();

        SVWorldDataManager<Marker> rebuilt = storeOn(storage, "blocks_close_twice");
        assertEquals("alice", rebuilt.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getOwner());
    }

    private static String nameOf(Marker marker) {
        return marker == null ? "none" : marker.getOwner();
    }
}
