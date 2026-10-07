package br.com.finalcraft.evernifecore.region.visual;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns a shape into the points of one outline frame. Points sit on the outer faces of the blocks (a box of blocks
 * {@code 0..9} is drawn from {@code 0} to {@code 10}), one every {@code step} blocks along each edge, only within
 * {@code radius} of the viewer, and never more than {@code budget} of them. A shape covering every Y is drawn over the
 * world's height; a whole-world shape has no outline. Pure and thread-safe.
 */
public final class OutlineSampler {

    private OutlineSampler() {
    }

    public static List<WorldLocPos> sample(Shape shape, String world, WorldHeight height, WorldLocPos viewer,
                                           int step, int radius, int budget) {
        if (!world.equals(viewer.getWorldName()) || budget <= 0) {
            return Collections.emptyList();
        }
        Frame frame = new Frame(world, viewer, Math.max(1, step), (double) radius * radius, budget);
        if (shape instanceof CuboidShape) {
            cuboid(frame, shape.getBounds(), height);
        } else if (shape instanceof PolygonShape) {
            polygon(frame, (PolygonShape) shape, height);
        }
        return frame.points;
    }

    private static void cuboid(Frame frame, BoundingBox box, WorldHeight height) {
        double x0 = box.getMinX(), x1 = box.getMaxX() + 1d;
        double z0 = box.getMinZ(), z1 = box.getMaxZ() + 1d;
        double y0 = box.isUnboundedY() ? height.getMinY() : box.getMinY();
        double y1 = (box.isUnboundedY() ? height.getMaxY() : box.getMaxY()) + 1d;
        double[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    frame.add(x, y, z);
                }
            }
        }
        for (double y : ys) {
            for (double z : zs) {
                frame.edge(x0, y, z, x1, y, z);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                frame.edge(x, y0, z, x, y1, z);
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                frame.edge(x, y, z0, x, y, z1);
            }
        }
    }

    private static void polygon(Frame frame, PolygonShape polygon, WorldHeight height) {
        double y0 = polygon.isUnboundedY() ? height.getMinY() : polygon.getMinY();
        double y1 = (polygon.isUnboundedY() ? height.getMaxY() : polygon.getMaxY()) + 1d;
        List<BlockPos> points = polygon.getPoints();
        for (BlockPos point : points) {
            frame.add(point.getX(), y0, point.getZ());
            frame.add(point.getX(), y1, point.getZ());
        }
        for (int i = 0; i < points.size(); i++) {
            BlockPos a = points.get(i);
            BlockPos b = points.get((i + 1) % points.size());
            frame.edge(a.getX(), y0, a.getZ(), a.getX(), y1, a.getZ());
            frame.edge(a.getX(), y0, a.getZ(), b.getX(), y0, b.getZ());
            frame.edge(a.getX(), y1, a.getZ(), b.getX(), y1, b.getZ());
        }
    }

    private static final class Frame {
        final String world;
        final WorldLocPos viewer;
        final int step;
        final double radiusSquared;
        final int budget;
        final List<WorldLocPos> points = new ArrayList<>();

        Frame(String world, WorldLocPos viewer, int step, double radiusSquared, int budget) {
            this.world = world;
            this.viewer = viewer;
            this.step = step;
            this.radiusSquared = radiusSquared;
            this.budget = budget;
        }

        /** The points strictly between both ends, {@code step} apart - the ends are vertices, added once each. */
        void edge(double ax, double ay, double az, double bx, double by, double bz) {
            double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
            for (double t = step; t < length && points.size() < budget; t += step) {
                double f = t / length;
                add(ax + (bx - ax) * f, ay + (by - ay) * f, az + (bz - az) * f);
            }
        }

        void add(double x, double y, double z) {
            if (points.size() >= budget) {
                return;
            }
            double dx = x - viewer.getX(), dy = y - viewer.getY(), dz = z - viewer.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                points.add(WorldLocPos.of(x, y, z, world));
            }
        }
    }
}
