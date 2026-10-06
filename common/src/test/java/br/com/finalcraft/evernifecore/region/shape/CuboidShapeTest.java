package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidShapeTest {

    private RegionGridOptions previousGrid;

    @BeforeEach
    void pinTheDefaultGrid() {
        previousGrid = RegionGridOptions.getCurrent();
        RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT_LEGACY);
    }

    // The grid is global static state other suites read; never leak a swapped one.
    @AfterEach
    void restoreGrid() {
        RegionGridOptions.setCurrent(previousGrid);
    }

    @Test
    void everyCornerIsInsideAndOneBlockPastAnyFaceIsNot() {
        CuboidShape box = CuboidShape.of(BlockPos.of(10, 70, -5), BlockPos.of(0, 60, 5));

        for (int x : new int[]{0, 10}) {
            for (int y : new int[]{60, 70}) {
                for (int z : new int[]{-5, 5}) {
                    assertTrue(box.contains(x, y, z), "corner " + x + "|" + y + "|" + z);
                }
            }
        }
        assertFalse(box.contains(-1, 65, 0));
        assertFalse(box.contains(11, 65, 0));
        assertFalse(box.contains(5, 59, 0));
        assertFalse(box.contains(5, 71, 0));
        assertFalse(box.contains(5, 65, -6));
        assertFalse(box.contains(5, 65, 6));
        assertEquals(BlockPos.of(0, 60, -5), box.getMin());
        assertEquals(BlockPos.of(10, 70, 5), box.getMax());
    }

    @Test
    void aDoublePointIsJudgedByTheBlockItIsIn() {
        CuboidShape box = CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(3, 3, 3));

        assertTrue(box.contains(3.99, 0.0, 0.5));
        assertFalse(box.contains(-0.01, 1.0, 1.0)); //floors to -1, not 0
    }

    @Test
    void anUnboundedYContainsAnyHeight() {
        CuboidShape column = CuboidShape.ofUnboundedY(BlockPos.of(0, 64, 0), BlockPos.of(9, 64, 9));

        assertTrue(column.contains(5, -5000, 5));
        assertTrue(column.contains(5, 5000, 5));
        assertFalse(column.contains(10, 64, 5));
        assertTrue(column.isUnboundedY());
        assertTrue(column.getBounds().isUnboundedY());
        assertEquals(Long.MAX_VALUE, column.getVolume());
    }

    static Stream<Arguments> grids() {
        return Stream.of(
                Arguments.of("MINECRAFT_LEGACY", RegionGridOptions.MINECRAFT_LEGACY),
                Arguments.of("MINECRAFT", RegionGridOptions.MINECRAFT),
                Arguments.of("HYTALE", RegionGridOptions.HYTALE)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grids")
    void theYRangeIsKeptAsGivenWhateverTheGrid(String gridName, RegionGridOptions grid) {
        RegionGridOptions.setCurrent(grid);

        CuboidShape tall = CuboidShape.of(BlockPos.of(0, -100, 0), BlockPos.of(1, 1000, 1));

        assertEquals(-100, tall.getMin().getY());
        assertEquals(1000, tall.getMax().getY());
        assertTrue(tall.contains(0, -100, 0));
        assertTrue(tall.contains(1, 1000, 1));
    }

    @Test
    void aBoxCrossingTwoByTwoChunksTouchesFour() {
        CuboidShape box = CuboidShape.of(BlockPos.of(10, 0, 10), BlockPos.of(20, 5, 20));

        List<ChunkPos> chunks = new ArrayList<>();
        box.getChunks().forEach(chunks::add);

        assertEquals(new HashSet<>(Arrays.asList(ChunkPos.of(0, 0), ChunkPos.of(1, 0), ChunkPos.of(0, 1), ChunkPos.of(1, 1))),
                new HashSet<>(chunks));
        assertEquals(4, chunks.size());
    }

    @Test
    void theVolumeOfAHugeBoxDoesNotOverflow() {
        CuboidShape huge = CuboidShape.of(BlockPos.of(0, -64, 0), BlockPos.of(99_999, 319, 99_999));

        assertEquals(100_000L * 384 * 100_000, huge.getVolume());
    }

    @Test
    void boxesSharingAFaceIntersectAndOneBlockApartDoNot() {
        CuboidShape left = CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(10, 10, 10));
        CuboidShape sharingFace = CuboidShape.of(BlockPos.of(10, 0, 0), BlockPos.of(20, 10, 10));
        CuboidShape oneBlockApart = CuboidShape.of(BlockPos.of(11, 0, 0), BlockPos.of(20, 10, 10));

        assertTrue(left.intersects(sharingFace));
        assertTrue(sharingFace.intersects(left));
        assertFalse(left.intersects(oneBlockApart));
        assertFalse(oneBlockApart.intersects(left));
    }
}
