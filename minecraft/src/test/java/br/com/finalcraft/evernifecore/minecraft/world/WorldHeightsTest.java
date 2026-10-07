package br.com.finalcraft.evernifecore.minecraft.world;

import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldHeightsTest {

    @Test
    void theTopBlockIsOneBelowMaxHeight() {
        World world = Doubles.of(World.class).returning("getMinHeight", -64).returning("getMaxHeight", 320).build();

        assertEquals(WorldHeight.of(-64, 319), WorldHeights.of(world));
    }
}
