package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A prism: a polygon on the X/Z plane extruded over a Y range. A block is inside when its X/Z point lies inside
 * the polygon or on its outline (edges and vertices count as inside), and its Y is in range.
 */
public final class PolygonShape implements Shape {

    private final List<BlockPos> points;
    private final long[] xs;
    private final long[] zs;
    private final int minY;
    private final int maxY;
    private final BoundingBox bounds;

    private PolygonShape(List<BlockPos> pointsXZ, int minY, int maxY, boolean unboundedY) {
        if (pointsXZ == null || pointsXZ.size() < 3) {
            throw new IllegalArgumentException("A PolygonShape needs at least 3 points, got "
                    + (pointsXZ == null ? "null" : pointsXZ.size() + " " + pointsXZ)
                    + " - for a box use CuboidShape.of(a, b) instead.");
        }
        List<BlockPos> flat = new ArrayList<>(pointsXZ.size());
        this.xs = new long[pointsXZ.size()];
        this.zs = new long[pointsXZ.size()];
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < pointsXZ.size(); i++) {
            BlockPos point = pointsXZ.get(i);
            flat.add(BlockPos.of(point.getX(), 0, point.getZ()));
            xs[i] = point.getX();
            zs[i] = point.getZ();
            minX = Math.min(minX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxX = Math.max(maxX, point.getX());
            maxZ = Math.max(maxZ, point.getZ());
        }
        this.points = Collections.unmodifiableList(flat);
        this.minY = unboundedY ? Integer.MIN_VALUE : Math.min(minY, maxY);
        this.maxY = unboundedY ? Integer.MAX_VALUE : Math.max(minY, maxY);
        this.bounds = unboundedY
                ? BoundingBox.ofUnboundedY(minX, minZ, maxX, maxZ)
                : BoundingBox.of(minX, this.minY, minZ, maxX, this.maxY, maxZ);
    }

    /**
     * @param pointsXZ the outline in order, at least 3 points; their Y is ignored
     * @param minY     one end of the Y range, inclusive (the two ends may come in any order)
     * @param maxY     the other end, inclusive
     */
    public static PolygonShape of(List<BlockPos> pointsXZ, int minY, int maxY) {
        return new PolygonShape(pointsXZ, minY, maxY, false);
    }

    /** As {@link #of}, covering every Y. */
    public static PolygonShape ofUnboundedY(List<BlockPos> pointsXZ) {
        return new PolygonShape(pointsXZ, 0, 0, true);
    }

    /** The outline, each point with Y = 0. */
    public List<BlockPos> getPoints() {
        return points;
    }

    /** The lowest Y inside, or {@link Integer#MIN_VALUE} when {@link #isUnboundedY()}. */
    public int getMinY() {
        return minY;
    }

    /** The highest Y inside, or {@link Integer#MAX_VALUE} when {@link #isUnboundedY()}. */
    public int getMaxY() {
        return maxY;
    }

    public boolean isUnboundedY() {
        return bounds.isUnboundedY();
    }

    @Override
    public ShapeType getType() {
        return ShapeType.POLYGON;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z) && containsXZ(x, z);
    }

    @Override
    public BoundingBox getBounds() {
        return bounds;
    }

    /** The chunks of the bounding box whose cell the polygon actually reaches - a concave notch leaves its chunks out. */
    @Override
    public Iterable<ChunkPos> getChunks() {
        // ponytail: tests every chunk of the bounding box; a huge polygon pays O(bbox chunks) here, fine for an index built once per change
        List<ChunkPos> touched = new ArrayList<>();
        for (ChunkPos chunk : bounds.getChunks()) {
            if (reachesCell(chunk.getXStart(), chunk.getZStart(), chunk.getXEnd(), chunk.getZEnd())) {
                touched.add(chunk);
            }
        }
        return touched;
    }

    /** The block count, by Pick's theorem over the integer outline, times the height. */
    @Override
    public long getVolume() {
        if (isUnboundedY()) {
            return UNBOUNDED_VOLUME;
        }
        long doubleArea = 0;
        long boundaryPoints = 0;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            doubleArea += xs[j] * zs[i] - xs[i] * zs[j];
            boundaryPoints += gcd(Math.abs(xs[i] - xs[j]), Math.abs(zs[i] - zs[j]));
        }
        long columns = (Math.abs(doubleArea) + boundaryPoints) / 2 + 1;
        try {
            return Math.multiplyExact(columns, (long) maxY - minY + 1);
        } catch (ArithmeticException tooBig) {
            return UNBOUNDED_VOLUME;
        }
    }

    private boolean containsXZ(long x, long z) {
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if (onSegment(x, z, xs[i], zs[i], xs[j], zs[j])) {
                return true; //the outline is inside; ray-casting alone would drop the right and top edges
            }
            //half-open on Z, so a ray through a vertex is counted once, by only one of its two edges
            if ((zs[i] > z) != (zs[j] > z)) {
                long left = (x - xs[i]) * (zs[j] - zs[i]);
                long right = (z - zs[i]) * (xs[j] - xs[i]);
                if (zs[j] > zs[i] ? left < right : left > right) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /** Whether the polygon reaches the closed cell [x0..x1] x [z0..z1]. */
    private boolean reachesCell(long x0, long z0, long x1, long z1) {
        if (containsXZ(x0, z0) || containsXZ(x1, z0) || containsXZ(x0, z1) || containsXZ(x1, z1)) {
            return true; //the cell sits (at least partly) inside the polygon
        }
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if (xs[i] >= x0 && xs[i] <= x1 && zs[i] >= z0 && zs[i] <= z1) {
                return true; //the polygon pokes a vertex into the cell
            }
            if (crosses(xs[i], zs[i], xs[j], zs[j], x0, z0, x1, z0) || crosses(xs[i], zs[i], xs[j], zs[j], x1, z0, x1, z1)
                    || crosses(xs[i], zs[i], xs[j], zs[j], x1, z1, x0, z1) || crosses(xs[i], zs[i], xs[j], zs[j], x0, z1, x0, z0)) {
                return true; //an edge cuts through the cell with both ends outside it
            }
        }
        return false;
    }

    /** Proper crossing only: touching cases are already caught by the corner and vertex checks. */
    private static boolean crosses(long ax, long az, long bx, long bz, long cx, long cz, long dx, long dz) {
        return Long.signum(cross(ax, az, bx, bz, cx, cz)) * Long.signum(cross(ax, az, bx, bz, dx, dz)) < 0
                && Long.signum(cross(cx, cz, dx, dz, ax, az)) * Long.signum(cross(cx, cz, dx, dz, bx, bz)) < 0;
    }

    private static long cross(long ox, long oz, long px, long pz, long qx, long qz) {
        return (px - ox) * (qz - oz) - (pz - oz) * (qx - ox);
    }

    private static boolean onSegment(long x, long z, long ax, long az, long bx, long bz) {
        return cross(ax, az, bx, bz, x, z) == 0
                && x >= Math.min(ax, bx) && x <= Math.max(ax, bx) && z >= Math.min(az, bz) && z <= Math.max(az, bz);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long rest = a % b;
            a = b;
            b = rest;
        }
        return a;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PolygonShape)) {
            return false;
        }
        PolygonShape polygon = (PolygonShape) other;
        return minY == polygon.minY && maxY == polygon.maxY && isUnboundedY() == polygon.isUnboundedY()
                && points.equals(polygon.points);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * points.hashCode() + minY) + maxY;
    }

    @Override
    public String toString() {
        return "PolygonShape[" + points + (isUnboundedY() ? ", unbounded Y" : ", y " + minY + ".." + maxY) + "]";
    }
}
