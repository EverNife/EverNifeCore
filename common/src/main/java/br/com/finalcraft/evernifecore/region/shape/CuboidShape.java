package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;

/**
 * An axis-aligned box of blocks, inclusive on every face. The Y range is kept exactly as given - it is never
 * clamped to a build height, so a shape stays valid on a world whose height differs from the grid default.
 */
public final class CuboidShape implements Shape {

    private final BlockPos min;
    private final BlockPos max;
    private final BoundingBox bounds;

    private CuboidShape(BlockPos min, BlockPos max, BoundingBox bounds) {
        this.min = min;
        this.max = max;
        this.bounds = bounds;
    }

    /** The box between two corners given in any order. */
    public static CuboidShape of(BlockPos a, BlockPos b) {
        BoundingBox bounds = BoundingBox.of(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        return new CuboidShape(BlockPos.of(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()),
                BlockPos.of(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()), bounds);
    }

    /**
     * The X/Z box between two corners, covering every Y. The corners' Y values are kept (sorted) for
     * {@link #getMin()}/{@link #getMax()} but ignored by {@link #contains}.
     */
    public static CuboidShape ofUnboundedY(BlockPos a, BlockPos b) {
        CuboidShape bounded = of(a, b);
        return new CuboidShape(bounded.min, bounded.max,
                BoundingBox.ofUnboundedY(bounded.min.getX(), bounded.min.getZ(), bounded.max.getX(), bounded.max.getZ()));
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }

    public boolean isUnboundedY() {
        return bounds.isUnboundedY();
    }

    @Override
    public ShapeType getType() {
        return ShapeType.CUBOID;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z);
    }

    @Override
    public BoundingBox getBounds() {
        return bounds;
    }

    @Override
    public Iterable<ChunkPos> getChunks() {
        return bounds.getChunks();
    }

    @Override
    public long getVolume() {
        return bounds.getVolume();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CuboidShape && bounds.equals(((CuboidShape) other).bounds)
                && min.equals(((CuboidShape) other).min) && max.equals(((CuboidShape) other).max);
    }

    @Override
    public int hashCode() {
        return 31 * bounds.hashCode() + min.hashCode();
    }

    @Override
    public String toString() {
        return "CuboidShape[" + min + " -> " + max + (isUnboundedY() ? ", unbounded Y" : "") + "]";
    }
}
