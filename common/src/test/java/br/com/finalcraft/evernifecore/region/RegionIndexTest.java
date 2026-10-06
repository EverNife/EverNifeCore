package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.RegionIndex.WorldSnapshot;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The snapshot mechanics: where a region is filed, what a write rebuilds, and what a concurrent reader sees. */
@ECoreTest
class RegionIndexTest {

    private static final String OWNER = "RegionIndexOwner";
    private static final String WORLD = "world";

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private RegionGridOptions previousGrid;

    @BeforeEach
    void setUp() {
        previousGrid = RegionGridOptions.getCurrent();
        RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT_LEGACY);
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
    }

    @AfterEach
    void tearDown() {
        Plugins.forget(OWNER);
        RegionGridOptions.setCurrent(previousGrid);
    }

    private Region region(String name, int x1, int z1, int x2, int z2) {
        return TestRegions.create(owner, name, WORLD, CuboidShape.of(BlockPos.of(x1, 0, z1), BlockPos.of(x2, 255, z2)));
    }

    @Test
    void aRegionAcrossTwoByTwoChunksIsFiledInExactlyThoseFourBuckets() {
        RegionIndex index = new RegionIndex();
        Region region = region("square", 10, 10, 20, 20);
        index.add(region);

        WorldSnapshot snapshot = index.snapshot(WORLD);
        assertEquals(new HashSet<>(Arrays.asList(RegionIndex.chunkKey(0, 0), RegionIndex.chunkKey(1, 0),
                RegionIndex.chunkKey(0, 1), RegionIndex.chunkKey(1, 1))), snapshot.buckets.keySet());
        for (Region[] bucket : snapshot.buckets.values()) {
            assertArrayEquals(new Region[]{region}, bucket);
        }
        assertEquals(0, snapshot.huge.length);
    }

    @Test
    void negativeChunksGetTheirOwnBuckets() {
        RegionIndex index = new RegionIndex();
        index.add(region("minus_minus", -16, -16, -1, -1));
        index.add(region("minus_zero", -16, 0, -1, 15));
        index.add(region("zero_minus", 0, -16, 15, -1));

        WorldSnapshot snapshot = index.snapshot(WORLD);
        assertEquals(3, snapshot.buckets.size());
        assertEquals("regionindexowner:minus_minus", snapshot.buckets.get(RegionIndex.chunkKey(-1, -1))[0].toString());
        assertEquals("regionindexowner:minus_zero", snapshot.buckets.get(RegionIndex.chunkKey(-1, 0))[0].toString());
        assertEquals("regionindexowner:zero_minus", snapshot.buckets.get(RegionIndex.chunkKey(0, -1))[0].toString());
    }

    @Test
    void aRegionOverTheChunkLimitIsHugeFoundByAtAndInNoBucket() {
        RegionIndex index = new RegionIndex(4);
        Region threeByThree = region("big", 0, 0, 47, 47);
        index.add(threeByThree);

        WorldSnapshot snapshot = index.snapshot(WORLD);
        assertArrayEquals(new Region[]{threeByThree}, snapshot.huge);
        assertTrue(snapshot.buckets.isEmpty());
        assertEquals(Arrays.asList(threeByThree), new RegionLookup(index).at(WORLD, 40, 64, 40));

        Region twoByTwo = region("edge", 0, 0, 31, 31); //exactly the limit is not over it
        index.add(twoByTwo);
        assertEquals(4, index.snapshot(WORLD).buckets.size());
    }

    @Test
    void replacingAShapeMovesTheRegionBetweenHugeAndTheBuckets() {
        RegionIndex index = new RegionIndex(4);
        Region region = region("shifting", 0, 0, 47, 47);
        index.add(region);

        region.setShape(CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(5, 255, 5)));
        index.replace(region);
        WorldSnapshot shrunk = index.snapshot(WORLD);
        assertEquals(0, shrunk.huge.length);
        assertArrayEquals(new Region[]{region}, shrunk.buckets.get(RegionIndex.chunkKey(0, 0)));
        assertEquals(1, shrunk.buckets.size());

        region.setShape(CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(47, 255, 47)));
        index.replace(region);
        WorldSnapshot grown = index.snapshot(WORLD);
        assertArrayEquals(new Region[]{region}, grown.huge);
        assertTrue(grown.buckets.isEmpty(), "the region must leave the bucket it had: " + grown.buckets.keySet());
    }

    @Test
    void everyPublicationRaisesTheVersionByOne() {
        RegionIndex index = new RegionIndex();
        assertEquals(0, index.snapshot(WORLD).version);

        Region first = region("first", 0, 0, 5, 5);
        index.add(first);
        assertEquals(1, index.snapshot(WORLD).version);
        index.add(region("second", 0, 0, 5, 5));
        assertEquals(2, index.snapshot(WORLD).version);
        index.replace(first);
        assertEquals(3, index.snapshot(WORLD).version);
        index.remove(first);
        assertEquals(4, index.snapshot(WORLD).version);
        index.rebuild(WORLD);
        assertEquals(5, index.snapshot(WORLD).version);
        assertEquals(0, index.snapshot("other_world").version);
    }

    @Test
    void aWriteLeavesTheBucketsOfOtherChunksAsTheSameObjects() {
        RegionIndex index = new RegionIndex();
        index.add(region("here", 0, 0, 5, 5));
        Region[] before = index.snapshot(WORLD).buckets.get(RegionIndex.chunkKey(0, 0));

        index.add(region("far_away", 80, 80, 85, 85));

        WorldSnapshot after = index.snapshot(WORLD);
        assertSame(before, after.buckets.get(RegionIndex.chunkKey(0, 0)));
        assertEquals(2, after.buckets.size());

        index.add(region("here_too", 1, 1, 2, 2));
        assertNotSame(before, index.snapshot(WORLD).buckets.get(RegionIndex.chunkKey(0, 0)));
    }

    @Test
    void bucketsOrderByPriorityThenChildBeforeParentThenId() {
        RegionIndex index = new RegionIndex();
        Region parent = region("a_parent", 0, 0, 15, 15);
        Region child = region("b_child", 0, 0, 15, 15);
        child.setParent(parent);
        Region sibling = region("c_sibling", 0, 0, 15, 15);
        Region alsoSibling = region("b_sibling", 0, 0, 15, 15);
        Region important = region("z_important", 0, 0, 15, 15);
        important.setPriority(10);
        for (Region region : Arrays.asList(parent, sibling, important, alsoSibling, child)) {
            index.add(region);
        }

        List<Region> expected = Arrays.asList(important, child, parent, alsoSibling, sibling);
        assertEquals(expected, Arrays.asList(index.snapshot(WORLD).buckets.get(RegionIndex.chunkKey(0, 0))));
        assertEquals(expected, new RegionLookup(index).at(WORLD, 3, 64, 3));
    }

    @Test
    void aSecondRegionWithTheSameIdIsRefused() {
        RegionIndex index = new RegionIndex();
        index.add(region("twin", 0, 0, 5, 5));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> index.add(region("twin", 9, 9, 9, 9)));
        assertTrue(refused.getMessage().contains("already indexed"), refused.getMessage());
    }

    @Test
    void concurrentReadersOnlyEverSeeWholeSnapshots() throws InterruptedException {
        RegionIndex index = new RegionIndex();
        RegionLookup lookup = new RegionLookup(index);
        Region anchor = region("anchor", 0, 0, 15, 15);
        index.add(anchor);

        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        threads.add(new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 500; i++) {
                    Region region = region("churn_" + i, 0, 0, 15 + i % 40, 15);
                    index.add(region);
                    if (i % 2 == 0) {
                        index.remove(region);
                    }
                }
            } catch (Throwable thrown) {
                errors.add(thrown);
            }
        }));
        for (int reader = 0; reader < 4; reader++) {
            threads.add(new Thread(() -> {
                try {
                    start.await();
                    long lastVersion = -1;
                    for (int i = 0; i < 5_000; i++) {
                        WorldSnapshot snapshot = index.snapshot(WORLD);
                        assertTrue(snapshot.version >= lastVersion, "version went back: " + lastVersion + " -> " + snapshot.version);
                        lastVersion = snapshot.version;
                        List<Region> found = lookup.at(snapshot, 5, 64, 5);
                        assertTrue(found.contains(anchor), "the anchor never leaves: " + found);
                        for (Region region : found) {
                            assertTrue(snapshot.rank.containsKey(region), region + " is not in the snapshot it was read from");
                        }
                    }
                } catch (Throwable thrown) {
                    errors.add(thrown);
                }
            }));
        }
        threads.forEach(Thread::start);
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(1 + 250, lookup.byWorld(WORLD).size());
        assertFalse(lookup.at(WORLD, 5, 64, 5).isEmpty());
    }
}
