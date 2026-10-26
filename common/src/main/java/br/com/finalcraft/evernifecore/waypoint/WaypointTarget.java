package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import jakarta.annotation.Nullable;

import java.util.function.Supplier;

/**
 * Where a waypoint points. {@link #locate()} is read again on every refresh, on the player's world thread, so a target
 * that moves is followed; null means the target is gone, and the waypoint hides itself.
 */
@FunctionalInterface
public interface WaypointTarget {

    @Nullable WorldLocPos locate();

    /** A fixed position. */
    static WaypointTarget at(WorldLocPos position) {
        return () -> position;
    }

    /**
     * The centre of {@code region}'s bounds, in its world. A region open in height is centred on the world's height
     * instead of on the middle of all integers.
     */
    static WaypointTarget region(Region region) {
        return () -> {
            BoundingBox bounds = region.getShape().getBounds();
            WorldHeight height = EverNifeCore.getPlatform().getWorldHeight(region.getWorld());
            int minY = Math.max(bounds.getMinY(), height.getMinY());
            int maxY = Math.min(bounds.getMaxY(), height.getMaxY());
            return WorldLocPos.of((bounds.getMinX() + (double) bounds.getMaxX() + 1) / 2, (minY + (double) maxY + 1) / 2,
                    (bounds.getMinZ() + (double) bounds.getMaxZ() + 1) / 2, region.getWorld());
        };
    }

    /** A moving target - an entity, an NPC: {@code live} is asked on every refresh, and answers null once it is gone. */
    static WaypointTarget following(Supplier<WorldLocPos> live) {
        return live::get;
    }
}
