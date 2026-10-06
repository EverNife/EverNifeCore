package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import br.com.finalcraft.evernifecore.region.shape.WorldShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in registrations for the region shapes. Every shape is a map with a {@code type} field
 * ({@code cuboid | polygon | world}); corners and points are compact {@code x|y|z} strings:
 * <pre>
 * { type: cuboid, min: "0|60|0", max: "40|90|40", unboundedY: false }
 * { type: polygon, points: ["0|0|0", "10|0|0", "10|0|10"], minY: 0, maxY: 64, unboundedY: false }
 * { type: world }
 * </pre>
 * The same map serves a concrete-type field and a {@link Shape} field: the latter picks the class from {@code type}.
 */
final class CFShapeFamily {

    private CFShapeFamily() {
    }

    static void register() {
        ConfigFactory.register(Shape.class).asMap(CFShapeFamily::encode, CFShapeFamily::decode);
        registerConcrete(CuboidShape.class);
        registerConcrete(PolygonShape.class);
        registerConcrete(WorldShape.class);
    }

    private static <T extends Shape> void registerConcrete(Class<T> type) {
        ConfigFactory.register(type).asMap(CFShapeFamily::encode, map -> {
            Shape shape = decode(map);
            if (!type.isInstance(shape)) {
                throw new IllegalArgumentException("Expected a " + type.getSimpleName() + " but the stored shape is a '"
                        + map.get("type") + "' - read it as Shape to accept any shape, or fix its 'type' field.");
            }
            return type.cast(shape);
        });
    }

    private static Map<String, Object> encode(Shape shape) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", shape.getType().getId());
        if (shape instanceof CuboidShape) {
            CuboidShape cuboid = (CuboidShape) shape;
            map.put("min", cuboid.getMin().serialize());
            map.put("max", cuboid.getMax().serialize());
            map.put("unboundedY", cuboid.isUnboundedY());
        } else if (shape instanceof PolygonShape) {
            PolygonShape polygon = (PolygonShape) shape;
            List<String> points = new ArrayList<>();
            for (BlockPos point : polygon.getPoints()) {
                points.add(point.serialize());
            }
            map.put("points", points);
            map.put("minY", polygon.getMinY());
            map.put("maxY", polygon.getMaxY());
            map.put("unboundedY", polygon.isUnboundedY());
        }
        return map;
    }

    private static Shape decode(Map<String, Object> map) {
        String type = String.valueOf(map.get("type"));
        boolean unboundedY = Boolean.TRUE.equals(map.get("unboundedY"));
        if (ShapeType.CUBOID.getId().equals(type)) {
            BlockPos min = BlockPos.deserialize(String.valueOf(map.get("min")));
            BlockPos max = BlockPos.deserialize(String.valueOf(map.get("max")));
            return unboundedY ? CuboidShape.ofUnboundedY(min, max) : CuboidShape.of(min, max);
        }
        if (ShapeType.POLYGON.getId().equals(type)) {
            List<BlockPos> points = new ArrayList<>();
            for (Object point : (List<?>) map.get("points")) {
                points.add(BlockPos.deserialize(String.valueOf(point)));
            }
            return unboundedY
                    ? PolygonShape.ofUnboundedY(points)
                    : PolygonShape.of(points, ((Number) map.get("minY")).intValue(), ((Number) map.get("maxY")).intValue());
        }
        if (ShapeType.WORLD.getId().equals(type)) {
            return WorldShape.INSTANCE;
        }
        throw new IllegalArgumentException("Unknown shape type '" + type + "' in " + map + " - the 'type' field must be one of "
                + Arrays.toString(new String[]{ShapeType.CUBOID.getId(), ShapeType.POLYGON.getId(), ShapeType.WORLD.getId()}) + ".");
    }
}
