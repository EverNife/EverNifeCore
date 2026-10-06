package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.WorldShape;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The read side: which regions each query answers, and what a region accepts as its parent. */
@ECoreTest
class RegionLookupTest {

    private static final String OWNER = "RegionLookupOwner";
    private static final String OTHER_OWNER = "RegionLookupOther";

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private ECPluginData otherOwner;
    private RegionIndex index;
    private RegionLookup lookup;
    private RegionGridOptions previousGrid;

    @BeforeEach
    void setUp() {
        previousGrid = RegionGridOptions.getCurrent();
        RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT_LEGACY);
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
        otherOwner = Plugins.fakePluginData(OTHER_OWNER, tempDir.resolve("other").toFile());
        index = new RegionIndex();
        lookup = new RegionLookup(index);
    }

    @AfterEach
    void tearDown() {
        Plugins.forget(OWNER);
        Plugins.forget(OTHER_OWNER);
        RegionGridOptions.setCurrent(previousGrid);
    }

    private Region add(ECPluginData by, String name, String world, Shape shape) {
        Region region = TestRegions.create(by, name, world, shape);
        index.add(region);
        return region;
    }

    private static CuboidShape box(int x1, int z1, int x2, int z2) {
        return CuboidShape.of(BlockPos.of(x1, 0, z1), BlockPos.of(x2, 100, z2));
    }

    @Test
    void atFindsTheRegionsOfAPointInEveryForm() {
        Region region = add(owner, "spawn", "world", box(0, 0, 9, 9));
        add(owner, "elsewhere", "world", box(50, 50, 60, 60));

        assertEquals(Arrays.asList(region), lookup.at("world", 9, 100, 0));
        assertEquals(Arrays.asList(region), lookup.at("world", 9.9, 100.5, 0.0));
        assertEquals(Collections.emptyList(), lookup.at("world", -0.1, 50.0, 5.0)); //floors to -1
        assertEquals(Arrays.asList(region), lookup.at(new WorldBlockPos(5, 50, 5, "world")));
        assertEquals(Arrays.asList(region), lookup.at(new WorldLocPos(5.5, 50.0, 5.5, "world")));
        assertEquals(Collections.emptyList(), lookup.at(new WorldBlockPos(5, 50, 5, "world_nether")));
        assertEquals(Collections.emptyList(), lookup.at("world", 5, 101, 5));
    }

    @Test
    void aWorldWideRegionIsEverywhereInItsWorldAndNowhereElse() {
        Region wilderness = add(owner, "wilderness", "world", WorldShape.INSTANCE);
        Region spawn = add(owner, "spawn", "world", box(0, 0, 9, 9));
        spawn.setPriority(5);
        index.replace(spawn);

        assertEquals(Arrays.asList(wilderness), lookup.at("world", 1_000_000, -500, -1_000_000));
        assertEquals(Arrays.asList(spawn, wilderness), lookup.at("world", 1, 1, 1));
        assertSame(Collections.emptyList(), lookup.at("world_nether", 1, 1, 1));
    }

    @Test
    void aQueryThatFindsNothingReturnsTheSharedEmptyList() {
        assertSame(Collections.emptyList(), lookup.at("never_had_a_region", 0, 0, 0));
        assertSame(lookup.at("never_had_a_region", 0, 0, 0), lookup.at("never_had_a_region", 99, 9, 99));

        add(owner, "spawn", "world", box(0, 0, 9, 9));
        assertSame(Collections.emptyList(), lookup.at("world", 500, 0, 500));
        assertSame(Collections.emptyList(), lookup.in("never_had_a_region", box(0, 0, 1, 1)));
    }

    @Test
    void inIsExactBetweenCuboids() {
        Region spawn = add(owner, "spawn", "world", box(0, 0, 9, 9));

        assertEquals(Arrays.asList(spawn), lookup.in("world", box(9, 9, 20, 20))); //shares the corner column
        assertEquals(Collections.emptyList(), lookup.in("world", box(10, 0, 20, 9))); //one block apart
        assertEquals(Collections.emptyList(), lookup.in("world", CuboidShape.of(BlockPos.of(0, 101, 0), BlockPos.of(9, 120, 9))));
        assertEquals(Arrays.asList(spawn), lookup.in("world", WorldShape.INSTANCE));
    }

    @Test
    void inIsConservativeForAPolygonAndReportsABoxThatOnlyTouchesItsBounds() {
        //a 64x64 L whose upper-right 32x32 quarter is cut away
        Region lShape = add(owner, "l_shape", "world", PolygonShape.of(Arrays.asList(BlockPos.of(0, 0, 0), BlockPos.of(64, 0, 0),
                BlockPos.of(64, 0, 32), BlockPos.of(32, 0, 32), BlockPos.of(32, 0, 64), BlockPos.of(0, 0, 64)), 0, 100));

        //a box in the notch but in a chunk the polygon also reaches (2|2, the notch corner): the bounds overlap,
        //the area does not - reported anyway, by design
        CuboidShape notchNearTheCorner = box(40, 40, 47, 47);
        assertTrue(lookup.at("world", 44, 50, 44).isEmpty(), "the notch is not part of the region");
        assertEquals(Arrays.asList(lShape), lookup.in("world", notchNearTheCorner));

        //a box in a chunk only the notch covers: the polygon is filed in no such bucket, so not even a candidate
        assertEquals(Collections.emptyList(), lookup.in("world", box(50, 50, 60, 60)));
        assertEquals(Collections.emptyList(), lookup.in("world", box(70, 70, 80, 80)));
    }

    @Test
    void aParentMustShareOwnerAndWorldAndNeverCloseACycle() {
        Region parent = add(owner, "parent", "world", box(0, 0, 9, 9));
        Region child = add(owner, "child", "world", box(1, 1, 2, 2));
        Region foreign = add(otherOwner, "foreign", "world", box(0, 0, 9, 9));
        Region nether = add(owner, "nether", "world_nether", box(0, 0, 9, 9));

        child.setParent(parent);
        assertSame(parent, child.getParent());

        assertTrue(assertThrows(IllegalArgumentException.class, () -> child.setParent(foreign)).getMessage().contains("same owner"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> child.setParent(nether)).getMessage().contains("same world"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parent.setParent(child)).getMessage().contains("cycle"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parent.setParent(parent)).getMessage().contains("cycle"));
        assertSame(parent, child.getParent(), "a refused parent leaves the old one in place");

        child.setParent(null);
        assertNull(child.getParent());
    }

    @Test
    void theSetQueriesAnswerByIdOwnerTagWorldAndAll() {
        Region spawn = add(owner, "spawn", "world", box(0, 0, 9, 9));
        spawn.addTag("safe");
        Region arena = add(owner, "arena", "world_nether", box(0, 0, 9, 9));
        Region foreign = add(otherOwner, "foreign", "world", box(20, 20, 29, 29));
        foreign.addTag("safe");

        assertSame(spawn, lookup.byId(RegionId.of(OWNER, "spawn")));
        assertSame(spawn, lookup.byId(RegionId.parse("regionlookupowner:SPAWN")));
        assertNull(lookup.byId(RegionId.of(OWNER, "missing")));
        assertEquals(new HashSet<>(Arrays.asList(spawn, arena)), new HashSet<>(lookup.byOwner(owner)));
        assertEquals(new HashSet<>(Arrays.asList(spawn, foreign)), new HashSet<>(lookup.byTag("safe")));
        assertEquals(new HashSet<>(Arrays.asList(spawn, foreign)), new HashSet<>(lookup.byWorld("world")));
        assertEquals(new HashSet<>(Arrays.asList(spawn, arena, foreign)), new HashSet<>(lookup.all()));

        index.remove(arena);
        assertNull(lookup.byId(arena.getId()));
        assertSame(Collections.emptyList(), lookup.byWorld("world_nether"));
        assertThrows(UnsupportedOperationException.class, () -> lookup.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> spawn.getTags().add("hijacked"));
    }

    @Test
    void aRegionChecksTheWorldOfAPosition() {
        Region spawn = add(owner, "spawn", "world", box(0, 0, 9, 9));

        assertTrue(spawn.contains(new WorldBlockPos(1, 1, 1, "world")));
        assertTrue(!spawn.contains(new WorldBlockPos(1, 1, 1, "world_the_end")));
        assertTrue(!spawn.contains(new WorldLocPos(1.0, 1.0, 1.0, "world_the_end")));
        assertEquals("regionlookupowner:spawn", spawn.toString());
    }
}
