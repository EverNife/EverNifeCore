package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.blockdata.SVWorldDataManager.FlushReport;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cube query: how far it reaches, that a cold cache answers exactly what a warm one does, and that
 * asking about a region the store knows nothing about creates nothing.
 */
@ECoreTest
class SVWorldDataManagerRangeTest extends BlockStoreTestBase {

    private static final String WORLD = "world";
    private static final BlockPos CENTER = BlockPos.of(0, 64, 0);

    /** The center, plus one block exactly 20 away on each side of each axis. */
    private static final List<BlockPos> AROUND_THE_CENTER = new ArrayList<>();

    static {
        AROUND_THE_CENTER.add(CENTER);
        AROUND_THE_CENTER.add(BlockPos.of(20, 64, 0));
        AROUND_THE_CENTER.add(BlockPos.of(-20, 64, 0));
        AROUND_THE_CENTER.add(BlockPos.of(0, 84, 0));
        AROUND_THE_CENTER.add(BlockPos.of(0, 44, 0));
        AROUND_THE_CENTER.add(BlockPos.of(0, 64, 20));
        AROUND_THE_CENTER.add(BlockPos.of(0, 64, -20));
    }

    @Test
    void theCubeReachesExactlyAsFarAsItsRadius() {
        SVWorldDataManager<Marker> store = storeWithNeighbours("blocks_range_border");

        assertEquals(positionsOf(CENTER), foundBy(store, 19),
                "at 19 the six neighbours are one block outside the cube - on every axis, height included");
        assertEquals(positionsOf(AROUND_THE_CENTER.toArray(new BlockPos[0])), foundBy(store, 20),
                "at 20 they are exactly on the face of it");
    }

    @Test
    void radiusZeroAsksAboutTheCenterBlockAlone() {
        SVWorldDataManager<Marker> store = storeWithNeighbours("blocks_range_zero");

        assertEquals(positionsOf(CENTER), foundBy(store, 0));
        assertTrue(store.getRange(WORLD, BlockPos.of(1, 64, 0), 0).join().isEmpty(),
                "a radius of zero over an empty block answers nothing");
    }

    @Test
    void aNegativeRadiusIsRefusedOnTheSpot() {
        SVWorldDataManager<Marker> store = storeWithNeighbours("blocks_range_negative");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> store.getRange(WORLD, CENTER, -1));

        assertTrue(refused.getMessage().contains("0"), refused.getMessage());
    }

    @Test
    void aColdCacheAnswersWhatAWarmOneDoes() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> writer = storeWithNeighbours(storage, "blocks_range_cold");
        writer.flush().join();
        writer.close();

        SVWorldDataManager<Marker> reopened = storeOn(storage, "blocks_range_cold");
        reopened.manager().clearCache();
        assertNull(reopened.peekBlock(WORLD, CENTER), "nothing is cached now - this is the cold read");

        Set<String> cold = foundBy(reopened, 20);
        Set<String> warm = foundBy(reopened, 20);

        assertEquals(positionsOf(AROUND_THE_CENTER.toArray(new BlockPos[0])), cold);
        assertEquals(cold, warm, "the chunks the first query pulled in must not change the answer");
    }

    @Test
    void theChunksOfAnEmptyRegionAreNeitherReadTwiceNorCreated() {
        ScriptedStorage backend = openScriptedStorage();
        SVWorldDataManager<Marker> store = rawStoreOn(backend, "blocks_range_empty");
        store.setBlock(WORLD, CENTER, new Marker("alice", 10)).join();
        store.flush().join();
        backend.resetCalls();

        assertTrue(store.getRange(WORLD, BlockPos.of(5000, 64, 5000), 32).join().isEmpty());

        FlushReport report = store.flush().join();
        assertEquals(0, report.getSavedChunks(), "a read never creates the chunk entities it did not find");
        assertEquals(0, report.getDeletedChunks());
        assertEquals(1, backend.callsTo("findMany"),
                "every chunk the cube covers is asked for in ONE call, however wide the radius is");
    }

    @Test
    void theRangeIsHandedOutAsAnImmutableList() {
        SVWorldDataManager<Marker> store = storeWithNeighbours("blocks_range_immutable");
        List<BlockRecord<Marker>> found = store.getRange(WORLD, CENTER, 20).join();

        assertThrows(UnsupportedOperationException.class, () -> found.add(null));
    }

    @Test
    void theWorldBlockPosOverloadAsksTheSameQuestion() {
        SVWorldDataManager<Marker> store = storeWithNeighbours("blocks_range_overload");

        assertEquals(store.getRange(WORLD, CENTER, 20).join().size(),
                store.getRange(CENTER.atWorld(WORLD), 20).join().size());
    }

    // -----------------------------------------------------------------------------------------------------
    //  Fixtures
    // -----------------------------------------------------------------------------------------------------

    private SVWorldDataManager<Marker> storeWithNeighbours(String collection) {
        return storeWithNeighbours(openStorage(BackendDefinition.memory()), collection);
    }

    /** The center block and its six neighbours, each in the chunk its coordinates fall in. */
    private SVWorldDataManager<Marker> storeWithNeighbours(ECStorage storage, String collection) {
        SVWorldDataManager<Marker> store = storeOn(storage, collection);
        for (BlockPos pos : AROUND_THE_CENTER) {
            store.setBlock(WORLD, pos, new Marker(pos.serialize(), 1)).join();
        }
        return store;
    }

    private static Set<String> foundBy(SVWorldDataManager<Marker> store, int radius) {
        Set<String> found = new TreeSet<>();
        for (BlockRecord<Marker> record : store.getRange(WORLD, CENTER, radius).join()) {
            assertEquals(WORLD, record.getWorld());
            assertEquals(record.getPos().serialize(), record.getValue().getOwner(),
                    "each record must carry the value stored for ITS block");
            found.add(record.getPos().serialize());
        }
        return found;
    }

    private static Set<String> positionsOf(BlockPos... positions) {
        Set<String> expected = new TreeSet<>();
        for (BlockPos pos : positions) {
            expected.add(pos.serialize());
        }
        return expected;
    }
}
