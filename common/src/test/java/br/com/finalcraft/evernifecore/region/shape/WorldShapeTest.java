package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldShapeTest {

    @Test
    void theWorldContainsEveryBlock() {
        assertTrue(WorldShape.INSTANCE.contains(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertTrue(WorldShape.INSTANCE.contains(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(WorldShape.INSTANCE.contains(0.5, -9000.0, 123.25));
        assertTrue(WorldShape.INSTANCE.getBounds().contains(Integer.MAX_VALUE, 0, Integer.MIN_VALUE));
    }

    @Test
    void theWorldTouchesNoChunkInParticular() {
        assertFalse(WorldShape.INSTANCE.getChunks().iterator().hasNext());
    }

    @Test
    void theWorldHasNoFiniteVolume() {
        assertEquals(Long.MAX_VALUE, WorldShape.INSTANCE.getVolume());
        assertEquals(Long.MAX_VALUE, WorldShape.INSTANCE.getBounds().getVolume());
        assertTrue(WorldShape.INSTANCE.getBounds().isUnboundedY());
    }

    @Test
    void theWorldIntersectsAnyShape() {
        assertTrue(WorldShape.INSTANCE.intersects(CuboidShape.of(BlockPos.of(5, 5, 5), BlockPos.of(5, 5, 5))));
    }

    @Test
    void aWorldHeightNeedsMinBelowMax() {
        WorldHeight height = WorldHeight.of(-64, 319);
        assertEquals(-64, height.getMinY());
        assertEquals(319, height.getMaxY());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> WorldHeight.of(319, -64));
        assertTrue(refused.getMessage().contains("swapped"), refused.getMessage());
    }
}
