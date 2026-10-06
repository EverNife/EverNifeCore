package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;

/**
 * An immutable, world-less volume of blocks. The world belongs to whoever holds the shape.
 *
 * <p>A shape with an unbounded Y has no height of its own: its bounds say so through
 * {@link BoundingBox#isUnboundedY()}, and code that has to materialize that height asks the platform for the
 * real world height ({@code IPlatform.getWorldHeight}).</p>
 */
public interface Shape {

    /** The volume a shape reports when it has no finite size. */
    long UNBOUNDED_VOLUME = Long.MAX_VALUE;

    ShapeType getType();

    /** Whether the block at these coordinates is inside; every face is inclusive. */
    boolean contains(int x, int y, int z);

    /** Whether the point is inside, judged by the block it is in (each coordinate floored). */
    default boolean contains(double x, double y, double z) {
        return contains((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    BoundingBox getBounds();

    /**
     * The chunks this shape touches, on the chunk grid in force ({@code RegionGridOptions.getCurrent()}) when
     * this is called. Lazy, so a huge shape costs nothing until iterated. A {@link WorldShape} has none.
     */
    Iterable<ChunkPos> getChunks();

    /** Whether the two shapes share a block: exact for two cuboids, conservative (by bounds) otherwise. */
    default boolean intersects(Shape other) {
        return getBounds().intersects(other.getBounds());
    }

    /** The number of blocks inside, or {@link #UNBOUNDED_VOLUME} when the shape has no finite size. */
    long getVolume();
}
