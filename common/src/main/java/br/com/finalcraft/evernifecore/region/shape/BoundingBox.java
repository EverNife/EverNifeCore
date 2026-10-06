package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The axis-aligned box around a {@link Shape}, inclusive on every face. An unbounded Y spans the whole
 * {@code int} range, so every comparison below works without special-casing it.
 */
public final class BoundingBox {

    /** The box of a shape that covers everything: every axis spans the whole {@code int} range. */
    public static final BoundingBox EVERYTHING = new BoundingBox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final boolean unboundedY;

    private BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean unboundedY) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.unboundedY = unboundedY;
    }

    /** A box between two corners given in any order. */
    public static BoundingBox of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new BoundingBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), false);
    }

    /** A box between two X/Z corners given in any order, spanning every Y. */
    public static BoundingBox ofUnboundedY(int x1, int z1, int x2, int z2) {
        return new BoundingBox(Math.min(x1, x2), Integer.MIN_VALUE, Math.min(z1, z2),
                Math.max(x1, x2), Integer.MAX_VALUE, Math.max(z1, z2), true);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /** Whether this box spans every Y; {@link #getMinY()}/{@link #getMaxY()} are then the {@code int} extremes. */
    public boolean isUnboundedY() {
        return unboundedY;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Whether the two boxes share at least one block; boxes that only touch on a face do. */
    public boolean intersects(BoundingBox other) {
        return minX <= other.maxX && other.minX <= maxX
                && minY <= other.maxY && other.minY <= maxY
                && minZ <= other.maxZ && other.minZ <= maxZ;
    }

    /** The block count, or {@link Shape#UNBOUNDED_VOLUME} when Y is unbounded or the count does not fit a {@code long}. */
    public long getVolume() {
        if (unboundedY) {
            return Shape.UNBOUNDED_VOLUME;
        }
        try {
            return Math.multiplyExact(Math.multiplyExact((long) maxX - minX + 1, (long) maxY - minY + 1), (long) maxZ - minZ + 1);
        } catch (ArithmeticException tooBig) {
            return Shape.UNBOUNDED_VOLUME;
        }
    }

    /**
     * Every chunk whose column this box crosses, on the chunk grid in force at the call, row by row. Lazy:
     * nothing is allocated until iterated.
     */
    public Iterable<ChunkPos> getChunks() {
        int shift = RegionGridOptions.getCurrent().getChunkShift();
        int minChunkX = minX >> shift, maxChunkX = maxX >> shift;
        int minChunkZ = minZ >> shift, maxChunkZ = maxZ >> shift;
        return () -> new Iterator<ChunkPos>() {
            private int chunkX = minChunkX;
            private int chunkZ = minChunkZ;

            @Override
            public boolean hasNext() {
                return chunkZ <= maxChunkZ;
            }

            @Override
            public ChunkPos next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                ChunkPos chunk = ChunkPos.of(chunkX, chunkZ);
                if (chunkX == maxChunkX) {
                    chunkX = minChunkX;
                    chunkZ++;
                } else {
                    chunkX++;
                }
                return chunk;
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BoundingBox)) {
            return false;
        }
        BoundingBox box = (BoundingBox) other;
        return minX == box.minX && minY == box.minY && minZ == box.minZ
                && maxX == box.maxX && maxY == box.maxY && maxZ == box.maxZ && unboundedY == box.unboundedY;
    }

    @Override
    public int hashCode() {
        int hash = minX;
        hash = 31 * hash + minY;
        hash = 31 * hash + minZ;
        hash = 31 * hash + maxX;
        hash = 31 * hash + maxY;
        hash = 31 * hash + maxZ;
        return 31 * hash + (unboundedY ? 1 : 0);
    }

    @Override
    public String toString() {
        return "BoundingBox[" + minX + "|" + (unboundedY ? "*" : String.valueOf(minY)) + "|" + minZ
                + " -> " + maxX + "|" + (unboundedY ? "*" : String.valueOf(maxY)) + "|" + maxZ + "]";
    }
}
