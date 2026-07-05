package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.MutableBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.MutableChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.WorldChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.MutableLocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.MutableRegionPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos;
import br.com.finalcraft.evernifecore.math.game.vector.region.WorldRegionPos;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Built-in registrations for the twelve position types. Each type keeps TWO forms: a MAP as a solo value or a
 * field (its canonical write form) and a compact STRING as a list element (via {@code serialize()} /
 * {@code deserialize(String)}). Every read is tolerant - a textual node is the legacy string form, an object
 * node the map form - so old files keep loading and a legacy string-list round-trips as a string-list.
 *
 * <p>One self-contained method per class (no shared factory interfaces): each {@code registerXxx} spells out
 * its own field set and constructor, so reading a single method tells the whole story of that type.
 */
final class CFPositionFamily {

    private CFPositionFamily() {
    }

    /** Register all twelve position types into {@link ConfigFactory}. */
    static void register() {
        registerBlockPos();
        registerMutableBlockPos();
        registerWorldBlockPos();

        registerLocPos();
        registerMutableLocPos();
        registerWorldLocPos();

        registerChunkPos();
        registerMutableChunkPos();
        registerWorldChunkPos();

        registerRegionPos();
        registerMutableRegionPos();
        registerWorldRegionPos();
    }

    // ==================== BlockPos family (integer x,y,z) ====================

    private static void registerBlockPos() {
        ConfigFactory.register(BlockPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("y", pos.getY());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    BlockPos.class,
                    BlockPos::deserialize,
                    node -> new BlockPos(
                        node.get("x").asInt(),
                        node.get("y").asInt(),
                        node.get("z").asInt())
                )
        ).asCompactElement(BlockPos::serialize, BlockPos::deserialize);
    }

    private static void registerMutableBlockPos() {
        ConfigFactory.register(MutableBlockPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("y", pos.getY());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    MutableBlockPos.class,
                    MutableBlockPos::deserialize,
                    node -> new MutableBlockPos(
                        node.get("x").asInt(),
                        node.get("y").asInt(),
                        node.get("z").asInt())
                )
        ).asCompactElement(MutableBlockPos::serialize, MutableBlockPos::deserialize);
    }

    private static void registerWorldBlockPos() {
        ConfigFactory.register(WorldBlockPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("y", pos.getY());
                    map.put("z", pos.getZ());
                    map.put("worldName", pos.getWorldName());
                    return map;
                }),
                tolerantDeserializer(
                    WorldBlockPos.class,
                    WorldBlockPos::deserialize,
                    node -> new WorldBlockPos(
                        node.get("x").asInt(),
                        node.get("y").asInt(),
                        node.get("z").asInt(),
                        stringOrNull(node.get("worldName")))
                )
        ).asCompactElement(WorldBlockPos::serialize, WorldBlockPos::deserialize);
    }

    // ==================== LocPos family (double x,y,z) ====================

    private static void registerLocPos() {
        ConfigFactory.register(LocPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("y", pos.getY());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    LocPos.class,
                    LocPos::deserialize,
                    node -> new LocPos(
                        node.get("x").asDouble(),
                        node.get("y").asDouble(),
                        node.get("z").asDouble())
                )
        ).asCompactElement(LocPos::serialize, LocPos::deserialize);
    }

    private static void registerMutableLocPos() {
        ConfigFactory.register(MutableLocPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("y", pos.getY());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    MutableLocPos.class,
                    MutableLocPos::deserialize,
                    node -> new MutableLocPos(
                        node.get("x").asDouble(),
                        node.get("y").asDouble(),
                        node.get("z").asDouble())
                )
        ).asCompactElement(MutableLocPos::serialize, MutableLocPos::deserialize);
    }

    private static void registerWorldLocPos() {
        ConfigFactory.register(WorldLocPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("y", pos.getY());
                    map.put("z", pos.getZ());
                    map.put("worldName", pos.getWorldName());
                    return map;
                }),
                tolerantDeserializer(
                    WorldLocPos.class,
                    WorldLocPos::deserialize,
                    node -> new WorldLocPos(
                        node.get("x").asDouble(),
                        node.get("y").asDouble(),
                        node.get("z").asDouble(),
                        stringOrNull(node.get("worldName")))
                )
        ).asCompactElement(WorldLocPos::serialize, WorldLocPos::deserialize);
    }

    // ==================== ChunkPos family (integer x,z) ====================

    private static void registerChunkPos() {
        ConfigFactory.register(ChunkPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    ChunkPos.class,
                    ChunkPos::deserialize,
                    node -> new ChunkPos(
                        node.get("x").asInt(),
                        node.get("z").asInt())
                )
        ).asCompactElement(ChunkPos::serialize, ChunkPos::deserialize);
    }

    private static void registerMutableChunkPos() {
        ConfigFactory.register(MutableChunkPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    MutableChunkPos.class,
                    MutableChunkPos::deserialize,
                    node -> new MutableChunkPos(
                        node.get("x").asInt(),
                        node.get("z").asInt())
                )
        ).asCompactElement(MutableChunkPos::serialize, MutableChunkPos::deserialize);
    }

    private static void registerWorldChunkPos() {
        ConfigFactory.register(WorldChunkPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("z", pos.getZ());
                    map.put("worldName", pos.getWorldName());
                    return map;
                }),
                tolerantDeserializer(
                    WorldChunkPos.class,
                    WorldChunkPos::deserialize,
                    node -> new WorldChunkPos(
                        node.get("x").asInt(), node.get("z").asInt(),
                        stringOrNull(node.get("worldName")))
                )
        ).asCompactElement(WorldChunkPos::serialize, WorldChunkPos::deserialize);
    }

    // ==================== RegionPos family (integer x,z) ====================

    private static void registerRegionPos() {
        ConfigFactory.register(RegionPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    RegionPos.class,
                    RegionPos::deserialize,
                    node -> new RegionPos(
                        node.get("x").asInt(), node.get("z").asInt())
                )
        ).asCompactElement(RegionPos::serialize, RegionPos::deserialize);
    }

    private static void registerMutableRegionPos() {
        ConfigFactory.register(MutableRegionPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("z", pos.getZ());
                    return map;
                }),
                tolerantDeserializer(
                    MutableRegionPos.class,
                    MutableRegionPos::deserialize,
                    node -> new MutableRegionPos(
                        node.get("x").asInt(), node.get("z").asInt())
                )
        ).asCompactElement(MutableRegionPos::serialize, MutableRegionPos::deserialize);
    }

    private static void registerWorldRegionPos() {
        ConfigFactory.register(WorldRegionPos.class).jackson(
                mapSerializer(pos -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", pos.getX());
                    map.put("z", pos.getZ());
                    map.put("worldName", pos.getWorldName());
                    return map;
                }),
                tolerantDeserializer(
                    WorldRegionPos.class,
                    WorldRegionPos::deserialize,
                    node -> new WorldRegionPos(
                        node.get("x").asInt(),
                        node.get("z").asInt(),
                        stringOrNull(node.get("worldName")))
                )
        ).asCompactElement(WorldRegionPos::serialize, WorldRegionPos::deserialize);
    }

    // ==================== shared Jackson glue ====================

    private static String stringOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /** Serializes a value to its canonical MAP form; nested values recurse through the mapper. */
    private static <T> JsonSerializer<T> mapSerializer(Function<T, Map<String, Object>> encode) {
        return new JsonSerializer<T>() {
            @Override
            public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeObject(encode.apply(value));
            }
        };
    }

    /**
     * A deserializer that reads BOTH the legacy string form and the map form: a textual node routes through the
     * string {@code deserialize}, an object node through {@code fromObject}. Keeps old on-disk files (a single
     * scalar OR a map) readable under the Jackson engine.
     */
    private static <T> StdDeserializer<T> tolerantDeserializer(Class<T> type, Function<String, T> deserialize,
                                                               Function<JsonNode, T> fromObject) {
        return new StdDeserializer<T>(type) {
            @Override
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                JsonNode node = parser.readValueAsTree();
                if (node.isTextual()) {
                    return deserialize.apply(node.asText());
                }
                return fromObject.apply(node);
            }
        };
    }
}
