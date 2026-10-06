package br.com.finalcraft.evernifecore.region.shape;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every shape crosses config and storage through the one {@code ConfigFactory} registration, and comes back equal. */
@ECoreTest
class ShapeCodecTest {

    private static final CuboidShape CUBOID = CuboidShape.of(BlockPos.of(0, 60, 0), BlockPos.of(40, 90, 40));
    private static final CuboidShape CUBOID_UNBOUNDED = CuboidShape.ofUnboundedY(BlockPos.of(-5, 64, -5), BlockPos.of(5, 64, 5));
    private static final PolygonShape POLYGON = PolygonShape.of(
            Arrays.asList(BlockPos.of(0, 0, 0), BlockPos.of(64, 0, 0), BlockPos.of(64, 0, 32), BlockPos.of(0, 0, 32)), -10, 100);
    private static final PolygonShape POLYGON_UNBOUNDED = PolygonShape.ofUnboundedY(
            Arrays.asList(BlockPos.of(0, 0, 0), BlockPos.of(10, 0, 0), BlockPos.of(10, 0, 10)));

    private static final List<Shape> EVERY_SHAPE = Arrays.asList(CUBOID, CUBOID_UNBOUNDED, POLYGON, POLYGON_UNBOUNDED, WorldShape.INSTANCE);

    private static Config open(Path dir) {
        return ConfigFactory.open(dir.resolve("shapes.yml"));
    }

    @Test
    void everyShapeRoundTripsThroughAConfigFileAsItsOwnClassAndAsShape(@TempDir Path dir) {
        Config config = open(dir);
        for (int i = 0; i < EVERY_SHAPE.size(); i++) {
            config.setValue("shape" + i, EVERY_SHAPE.get(i));
        }
        config.save();

        Config reopened = open(dir);
        for (int i = 0; i < EVERY_SHAPE.size(); i++) {
            Shape original = EVERY_SHAPE.get(i);
            assertEquals(original, reopened.getValue("shape" + i, original.getClass()));
            Shape asShape = reopened.getValue("shape" + i, Shape.class);
            assertEquals(original.getClass(), asShape.getClass());
            assertEquals(original, asShape);
        }
        assertSame(WorldShape.INSTANCE, reopened.getValue("shape4", Shape.class));
    }

    @Test
    void theCuboidIsWrittenInTheRegionRecordForm(@TempDir Path dir) {
        Config config = open(dir);
        config.setValue("cuboid", CUBOID);
        config.save();

        Map<?, ?> written = open(dir).getValue("cuboid", LinkedHashMap.class);

        assertEquals(Arrays.asList("type", "min", "max", "unboundedY"), Arrays.asList(written.keySet().toArray()));
        assertEquals("cuboid", written.get("type"));
        assertEquals("0|60|0", written.get("min"));
        assertEquals("40|90|40", written.get("max"));
        assertEquals(false, written.get("unboundedY"));
    }

    @Test
    void aShapeFieldDecodesTheClassItsTypeNames(@TempDir Path dir) {
        Config config = open(dir);
        config.setValue("cuboid", new ShapeRecord("a", CUBOID));
        config.setValue("polygon", new ShapeRecord("b", POLYGON));
        config.save();

        Config reopened = open(dir);
        assertEquals(CUBOID, reopened.getValue("cuboid", ShapeRecord.class).shape);
        assertEquals(POLYGON, reopened.getValue("polygon", ShapeRecord.class).shape);
    }

    @Test
    void everyShapeRoundTripsThroughTheStorageDefaultCodec() {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        try {
            Codec<Shape> codec = storage.defaultCodec(Shape.class);
            for (Shape shape : EVERY_SHAPE) {
                assertEquals(shape, codec.decode(codec.encode(shape)));
            }
            String json = new String(codec.encode(CUBOID), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"type\":\"cuboid\"") && json.contains("\"min\":\"0|60|0\""), json);

            EntityDescriptor<String, ShapeRecord> records = EntityDescriptor.builder(String.class, ShapeRecord.class)
                    .collection("shape_records")
                    .keyExtractor(record -> record.id)
                    .codec(storage.defaultCodec(ShapeRecord.class))
                    .build();
            CachingManager<String, ShapeRecord> manager = storage.manager(records, CacheOptions.of(CachePolicy.always()));
            for (int i = 0; i < EVERY_SHAPE.size(); i++) {
                manager.saveAndCache(new ShapeRecord("r" + i, EVERY_SHAPE.get(i))).join();
            }

            //re-read from the backend, not the cache
            manager.clearCache();
            for (int i = 0; i < EVERY_SHAPE.size(); i++) {
                assertEquals(EVERY_SHAPE.get(i), manager.refresh("r" + i).join().shape);
            }
        } finally {
            storage.close().join();
        }
    }

    /** An entity holding a shape behind its interface type - the field a region record has. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class ShapeRecord {
        public String id;
        public Shape shape;

        public ShapeRecord() {
        }

        ShapeRecord(String id, Shape shape) {
            this.id = id;
            this.shape = shape;
        }
    }
}
