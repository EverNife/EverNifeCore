package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolygonShapeTest {

    private static final PolygonShape SQUARE = PolygonShape.of(points(0, 0, 10, 0, 10, 10, 0, 10), 0, 20);

    /** A 64x64 square with its upper-right 32x32 quarter cut away. */
    private static final PolygonShape L_SHAPE = PolygonShape.of(points(0, 0, 64, 0, 64, 32, 32, 32, 32, 64, 0, 64), 0, 10);

    private RegionGridOptions previousGrid;

    @BeforeEach
    void pinTheDefaultGrid() {
        previousGrid = RegionGridOptions.getCurrent();
        RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT_LEGACY);
    }

    @AfterEach
    void restoreGrid() {
        RegionGridOptions.setCurrent(previousGrid);
    }

    @Test
    void aPointWellInsideIsInside() {
        assertTrue(SQUARE.contains(5, 10, 5));
    }

    @Test
    void aPointOnAnEdgeIsInside() {
        //the right edge: plain ray-casting misses this one
        assertTrue(SQUARE.contains(10, 10, 5));
        assertTrue(SQUARE.contains(5, 10, 10));
        assertTrue(SQUARE.contains(0, 10, 5));
    }

    @Test
    void aPointOutsideIsOutside() {
        assertFalse(SQUARE.contains(11, 10, 5));
        assertFalse(SQUARE.contains(5, 10, -1));
        assertFalse(SQUARE.contains(5, 21, 5)); //above the Y range
    }

    @Test
    void aVertexIsInside() {
        assertTrue(SQUARE.contains(10, 0, 10));
        assertTrue(SQUARE.contains(0, 0, 0));
    }

    @Test
    void theNotchOfAConcaveShapeIsOutside() {
        assertFalse(L_SHAPE.contains(50, 5, 50));
        assertTrue(L_SHAPE.contains(10, 5, 50));
        assertTrue(L_SHAPE.contains(50, 5, 10));
        //a ray from inside the lower arm runs through the notch's corner vertex (32,32)
        assertTrue(L_SHAPE.contains(20, 5, 32));
    }

    @Test
    void theChunksOfTheNotchAreLeftOut() {
        List<ChunkPos> chunks = new ArrayList<>();
        L_SHAPE.getChunks().forEach(chunks::add);

        //the 5x5 chunk box minus the four chunks the notch covers alone
        assertEquals(21, chunks.size(), chunks.toString());
        for (ChunkPos notchOnly : Arrays.asList(ChunkPos.of(3, 3), ChunkPos.of(3, 4), ChunkPos.of(4, 3), ChunkPos.of(4, 4))) {
            assertFalse(chunks.contains(notchOnly), notchOnly + " lies wholly in the notch");
        }
        assertTrue(chunks.contains(ChunkPos.of(2, 2)), "the notch's corner vertex sits in chunk 2|2");
        assertTrue(chunks.contains(ChunkPos.of(4, 2)), "the vertex 64|32 sits in chunk 4|2");
    }

    @Test
    void fewerThanThreePointsIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> PolygonShape.of(points(0, 0, 10, 10), 0, 10));

        assertTrue(refused.getMessage().contains("at least 3 points"), refused.getMessage());
    }

    @Test
    void theVolumeCountsEveryBlockOfThePrism() {
        assertEquals(11L * 11 * 21, SQUARE.getVolume());
        assertEquals(Long.MAX_VALUE, PolygonShape.ofUnboundedY(points(0, 0, 10, 0, 10, 10)).getVolume());
    }

    @Test
    void theBoundsCoverThePolygonAndTheYRange() {
        assertEquals(BoundingBox.of(0, 0, 0, 10, 20, 10), SQUARE.getBounds());
        assertTrue(PolygonShape.ofUnboundedY(points(0, 0, 10, 0, 10, 10)).getBounds().isUnboundedY());
    }

    private static List<BlockPos> points(int... xz) {
        List<BlockPos> points = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            points.add(BlockPos.of(xz[i], 64, xz[i + 1]));
        }
        return points;
    }
}
