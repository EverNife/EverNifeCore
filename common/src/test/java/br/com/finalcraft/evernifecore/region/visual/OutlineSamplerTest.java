package br.com.finalcraft.evernifecore.region.visual;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.region.shape.WorldShape;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One frame of an outline: which points, how many, and which are left out. */
class OutlineSamplerTest {

    private static final WorldHeight HEIGHT = WorldHeight.of(0, 255);
    private static final CuboidShape BOX = CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(9, 9, 9));
    private static final WorldLocPos CENTER = WorldLocPos.of(5, 5, 5, "world");

    private static List<WorldLocPos> sample(Shape shape, WorldLocPos viewer, int step, int radius, int budget) {
        return OutlineSampler.sample(shape, "world", HEIGHT, viewer, step, radius, budget);
    }

    @Test
    void cuboidEdgesAtStepOneCountTheirBlocks() {
        List<WorldLocPos> points = sample(BOX, CENTER, 1, 1000, 10_000);

        //8 corners on the outer faces (0 and 10), plus the 9 points strictly inside each of the 12 edges
        assertEquals(8 + 12 * 9, points.size());
        assertEquals(points.size(), new HashSet<>(points).size(), "no corner is drawn twice");
        assertTrue(points.contains(WorldLocPos.of(10, 10, 10, "world")));
        assertTrue(points.contains(WorldLocPos.of(0, 0, 0, "world")));
    }

    @Test
    void stepTwoHalvesThePoints() {
        assertEquals(8 + 12 * 4, sample(BOX, CENTER, 2, 1000, 10_000).size());
    }

    @Test
    void radiusDropsFarEdges() {
        CuboidShape longBox = CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(9, 9, 99));
        WorldLocPos nearTheSouthFace = WorldLocPos.of(5, 5, -5, "world");

        List<WorldLocPos> points = sample(longBox, nearTheSouthFace, 1, 20, 10_000);

        assertFalse(points.isEmpty());
        assertTrue(points.stream().allMatch(p -> p.getZ() < 20), "only the face near the viewer");
        assertTrue(points.size() < sample(longBox, nearTheSouthFace, 1, 1000, 10_000).size());
    }

    @Test
    void budgetCapsTheFrame() {
        assertEquals(50, sample(BOX, CENTER, 1, 1000, 50).size());
    }

    @Test
    void unboundedYUsesTheWorldHeight() {
        CuboidShape column = CuboidShape.ofUnboundedY(BlockPos.of(0, 0, 0), BlockPos.of(9, 0, 9));

        List<WorldLocPos> points = OutlineSampler.sample(column, "world", WorldHeight.of(-64, 319),
                WorldLocPos.of(5, 100, 5, "world"), 1, 10_000, 100_000);

        assertEquals(-64, points.stream().mapToDouble(WorldLocPos::getY).min().getAsDouble());
        assertEquals(320, points.stream().mapToDouble(WorldLocPos::getY).max().getAsDouble());
    }

    @Test
    void polygonDrawsAVerticalEdgePerVertex() {
        List<BlockPos> triangle = Arrays.asList(BlockPos.of(0, 0, 0), BlockPos.of(20, 0, 0), BlockPos.of(0, 0, 20));
        PolygonShape polygon = PolygonShape.of(triangle, 0, 9);

        List<WorldLocPos> points = sample(polygon, CENTER, 1, 1000, 10_000);

        for (BlockPos vertex : triangle) {
            long column = points.stream().filter(p -> p.getX() == vertex.getX() && p.getZ() == vertex.getZ()).count();
            assertEquals(11, column, "the vertical edge at " + vertex + " runs from 0 to 10, ends included");
        }
    }

    @Test
    void worldShapeHasNoOutline() {
        assertTrue(sample(WorldShape.INSTANCE, CENTER, 1, 1000, 10_000).isEmpty());
        assertFalse(sample(BOX, CENTER, 1, 1000, 10_000).isEmpty());
        assertTrue(OutlineSampler.sample(BOX, "world", HEIGHT, WorldLocPos.of(5, 5, 5, "nether"), 1, 1000, 10_000).isEmpty(),
                "a viewer in another world sees nothing");
    }
}
