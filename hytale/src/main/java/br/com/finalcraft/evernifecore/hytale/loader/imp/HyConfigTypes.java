package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.hytale.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.hytale.itemstack.ComparableItem;
import br.com.finalcraft.evernifecore.hytale.itemstack.ComparableItemComplex;
import br.com.finalcraft.evernifecore.hytale.itemstack.FCItemFactory;
import br.com.finalcraft.everylibs.util.FCInputReader;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.hypixel.hytale.math.vector.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The Hytale platform's config types for {@link ConfigFactory}. Ported from the legacy {@code @Loadable}/
 * {@code Salvable} registrations so that config files written by the old engine keep reading correctly under
 * the Jackson engine. The vector families persist as objects ({@code {x,y[,z]}}) for a solo value and a
 * compact space-separated string ({@code "x y z"}) as a list element; {@code Location} as an object
 * ({@code {worldName, position, rotation}}) or the compact string {@code WORLD|x y z xRot yRot zRot}; an
 * {@code ItemStack} as an item-data string-list.
 *
 * <p>The {@code GenericInventory}/{@code FCPlayerInventory} families are NOT registered here: they self-describe
 * as bound entities ({@code @JsonAnyGetter} + {@code ConfigLifecycle}), so nesting composes for free.
 */
public final class HyConfigTypes {

    private HyConfigTypes() {
    }

    public static void register() {
        registerVectors();
        registerLocation();
        registerItemStack();
        registerComparableItems();
    }

    // ==================== ComparableItem / ComparableItemComplex ====================

    /** Both persist as their compact item-id (plus metadata for the complex one) string. */
    private static void registerComparableItems() {
        ConfigFactory.register(ComparableItem.class).asString(
                ComparableItem::serialize,
                ComparableItem::deserialize
        );
        ConfigFactory.register(ComparableItemComplex.class).asString(
                ComparableItemComplex::serialize,
                ComparableItemComplex::deserialize
        );
    }

    // ==================== vector families ====================

    private static void registerVectors() {
        // Each vector keeps its rich {x,y[,z]} map as a solo value/field, and a compact space-separated string
        // ("x y z") when it is a list element (via asCompactElement). Compact parsing mirrors each type's
        // numeric kind (int/float/double) so a value round-trips.
        ConfigFactory.register(Vector3d.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Vector3d.class, node -> new Vector3d(
                        node.get("x").asDouble(), node.get("y").asDouble(), node.get("z").asDouble()))
        ).asCompactElement(
                v -> v.x() + " " + v.y() + " " + v.z(),
                s -> { String[] p = coords(s); return new Vector3d(
                        Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])); }
        );

        ConfigFactory.register(Vector3i.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Vector3i.class, node -> new Vector3i(
                        node.get("x").asInt(), node.get("y").asInt(), node.get("z").asInt()))
        ).asCompactElement(
                v -> v.x() + " " + v.y() + " " + v.z(),
                s -> { String[] p = coords(s); return new Vector3i(
                        Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); }
        );

        ConfigFactory.register(Vector3f.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Vector3f.class, node -> new Vector3f(
                        (float) node.get("x").asDouble(), (float) node.get("y").asDouble(),
                        (float) node.get("z").asDouble()))
        ).asCompactElement(
                v -> v.x() + " " + v.y() + " " + v.z(),
                s -> { String[] p = coords(s); return new Vector3f(
                        Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])); }
        );

        ConfigFactory.register(Rotation3f.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Rotation3f.class, node -> new Rotation3f(
                        (float) node.get("x").asDouble(), (float) node.get("y").asDouble(),
                        (float) node.get("z").asDouble()))
        ).asCompactElement(
                v -> v.x() + " " + v.y() + " " + v.z(),
                s -> { String[] p = coords(s); return new Rotation3f(
                        Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])); }
        );

        ConfigFactory.register(Vector2d.class).jackson(
                mapSerializer(v -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("x", v.x());
                    map.put("y", v.y());
                    return map;
                }),
                objectDeserializer(Vector2d.class, node -> new Vector2d(
                        node.get("x").asInt(), node.get("y").asInt()))
        ).asCompactElement(
                v -> v.x() + " " + v.y(),
                s -> { String[] p = coords(s); return new Vector2d(
                        Double.parseDouble(p[0]), Double.parseDouble(p[1])); }
        );
    }

    private static Map<String, Object> vec3(Object x, Object y, Object z) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        return map;
    }

    /** Split a compact vector string into its space-separated components. */
    private static String[] coords(String s) {
        return s.trim().split(" ");
    }

    // ==================== Location ====================

    private static void registerLocation() {
        ConfigFactory.register(Location.class).jackson(
                new JsonSerializer<Location>() {
                    @Override
                    public void serialize(Location value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("worldName", value.getWorld());
                        map.put("position", value.getPosition());
                        map.put("rotation", value.getRotation());
                        gen.writeObject(map);
                    }
                },
                new StdDeserializer<Location>(Location.class) {
                    @Override
                    public Location deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        JsonNode node = parser.readValueAsTree();
                        if (node.isTextual()) {
                            return fromLegacyString(node.asText());
                        }
                        Vector3d position = context.readTreeAsValue(node.get("position"), Vector3d.class);
                        Rotation3f rotation = context.readTreeAsValue(node.get("rotation"), Rotation3f.class);
                        return new Location(stringOrNull(node.get("worldName")), position, rotation);
                    }
                }
        ).asCompactElement(HyConfigTypes::compactLocation, HyConfigTypes::fromLegacyString);
    }

    /** The compact list-element form {@code WORLD|x y z xRot yRot zRot} - the exact inverse of
     *  {@link #fromLegacyString} (no space around the pipe, so the coord split stays clean). */
    private static String compactLocation(Location value) {
        Vector3d position = value.getPosition();
        Rotation3f rotation = value.getRotation();
        return value.getWorld() + "|"
                + position.x() + " " + position.y() + " " + position.z() + " "
                + rotation.x() + " " + rotation.y() + " " + rotation.z();
    }

    /** Parse the compact string form {@code WORLD|x y z xRot yRot zRot}. */
    private static Location fromLegacyString(String serializedLocation) {
        String[] split = serializedLocation.split(Pattern.quote("|"));
        String[] splitCoords = split[1].split(" ");

        String world = split[0].trim();
        Double x = FCInputReader.parseDouble(splitCoords[0]);
        Double y = FCInputReader.parseDouble(splitCoords[1]);
        Double z = FCInputReader.parseDouble(splitCoords[2]);
        Double xRotation = FCInputReader.parseDouble(splitCoords[3]);
        Double yRotation = FCInputReader.parseDouble(splitCoords[4]);
        Double zRotation = FCInputReader.parseDouble(splitCoords[5]);

        Vector3d position = new Vector3d(x, y, z);
        Rotation3f rotation = new Rotation3f(xRotation.floatValue(), yRotation.floatValue(), zRotation.floatValue());
        return new Location(world, position, rotation);
    }

    // ==================== ItemStack ====================

    private static void registerItemStack() {
        ConfigFactory.register(ItemStack.class).jackson(
                new JsonSerializer<ItemStack>() {
                    @Override
                    public void serialize(ItemStack value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        gen.writeObject(ItemDataPart.readItem(value));
                    }
                },
                new StdDeserializer<ItemStack>(ItemStack.class) {
                    @Override
                    public ItemStack deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        JsonNode node = parser.readValueAsTree();
                        List<String> itemData = new ArrayList<>();
                        if (node.isArray()) {
                            for (JsonNode element : node) {
                                itemData.add(element.asText());
                            }
                        }
                        return FCItemFactory.from(itemData).build();
                    }
                }
        );
    }

    // GenericInventory and FCPlayerInventory are self-describing bound entities (see their @JsonAnyGetter /
    // ConfigLifecycle) - they need no central registration here.

    // ==================== helpers ====================

    private static String stringOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static <T> JsonSerializer<T> mapSerializer(Function<T, Map<String, Object>> encode) {
        return new JsonSerializer<T>() {
            @Override
            public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeObject(encode.apply(value));
            }
        };
    }

    private static <T> StdDeserializer<T> objectDeserializer(Class<T> type,
                                                             Function<JsonNode, T> fromObject) {
        return new StdDeserializer<T>(type) {
            @Override
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return fromObject.apply(parser.readValueAsTree());
            }
        };
    }
}
