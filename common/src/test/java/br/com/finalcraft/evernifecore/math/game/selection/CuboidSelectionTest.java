package br.com.finalcraft.evernifecore.math.game.selection;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidSelectionTest {

    private RegionGridOptions previousGrid;

    @BeforeEach
    void rememberGrid() {
        previousGrid = RegionGridOptions.getCurrent();
    }

    // The grid is global static state other suites read; never leak a swapped one.
    @AfterEach
    void restoreGrid() {
        RegionGridOptions.setCurrent(previousGrid);
    }

    static Stream<Arguments> grids() {
        return Stream.of(
                Arguments.of("MINECRAFT_LEGACY", RegionGridOptions.MINECRAFT_LEGACY, 0, 255),
                Arguments.of("MINECRAFT", RegionGridOptions.MINECRAFT, -64, 319),
                Arguments.of("HYTALE", RegionGridOptions.HYTALE, 0, 319)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grids")
    void gridHasExpectedInclusiveHeightRange(String name, RegionGridOptions grid, int expectedMin, int expectedMax) {
        assertTrue(grid.getMinHeight() < grid.getMaxHeight(), name + " has min >= max");
        assertEquals(expectedMin, grid.getMinHeight());
        assertEquals(expectedMax, grid.getMaxHeight());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grids")
    void preservesYRangeInsideTheWorld(String name, RegionGridOptions grid, int min, int max) {
        RegionGridOptions.setCurrent(grid);

        CuboidSelection selection = CuboidSelection.of(BlockPos.of(0, 64, 0), BlockPos.of(10, 70, 10));

        assertEquals(64, selection.getMinium().getY());
        assertEquals(70, selection.getMaximum().getY());
        assertEquals(11 * 7 * 11, selection.getVolume());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grids")
    void clampsOnlyWhatIsOutsideTheWorld(String name, RegionGridOptions grid, int min, int max) {
        RegionGridOptions.setCurrent(grid);

        CuboidSelection below = CuboidSelection.of(BlockPos.of(0, -10_000, 0), BlockPos.of(1, 100, 1));
        assertEquals(min, below.getMinium().getY());
        assertEquals(100, below.getMaximum().getY());

        CuboidSelection above = CuboidSelection.of(BlockPos.of(0, 100, 0), BlockPos.of(1, 10_000, 1));
        assertEquals(100, above.getMinium().getY());
        assertEquals(max, above.getMaximum().getY());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grids")
    void expandVertReachesTheGridLimits(String name, RegionGridOptions grid, int min, int max) {
        RegionGridOptions.setCurrent(grid);

        CuboidSelection selection = CuboidSelection.of(BlockPos.of(0, 64, 0), BlockPos.of(10, 70, 10)).expandVert();

        assertEquals(min, selection.getMinium().getY());
        assertEquals(max, selection.getMaximum().getY());
    }

    @Test
    void constructorRejectsMinNotBelowMax() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RegionGridOptions(16, 10, 5));

        assertTrue(error.getMessage().contains("10"), error.getMessage());
        assertTrue(error.getMessage().contains("5"), error.getMessage());
        assertTrue(error.getMessage().contains("(chunkSize, minHeight, maxHeight)"), error.getMessage());

        assertThrows(IllegalArgumentException.class, () -> new RegionGridOptions(16, 7, 7));
    }
}
