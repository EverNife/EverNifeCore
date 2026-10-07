package br.com.finalcraft.evernifecore.minecraft.world;

import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import org.bukkit.World;

/**
 * Lives in this module because its API pin declares {@code World.getMinHeight()}, which arrived in 1.17: a server
 * older than that never links this class, since the caller picks it by version.
 */
public final class WorldHeights {

    private WorldHeights() {
    }

    /** The real floor and ceiling of {@code world}; {@code getMaxHeight()} is exclusive, so the top block is one below it. */
    public static WorldHeight of(World world) {
        return WorldHeight.of(world.getMinHeight(), world.getMaxHeight() - 1);
    }
}
