package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.hytale.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.hytale.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.ExtraInvManager;
import br.com.finalcraft.evernifecore.hytale.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.hytale.inventory.player.FCPlayerInventory;
import br.com.finalcraft.evernifecore.hytale.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.hytale.itemstack.ComparableItem;
import br.com.finalcraft.evernifecore.hytale.itemstack.ComparableItemComplex;
import br.com.finalcraft.evernifecore.hytale.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCInputReader;
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
 * the Jackson engine. The vector families persist as objects ({@code {x,y[,z]}}); {@code Location} as an
 * object ({@code {worldName, position, rotation}}) or the compact string {@code WORLD | x y z xRot yRot zRot};
 * an {@code ItemStack} as an item-data string-list.
 *
 * <p><b>Inventory families</b> ({@link GenericInventory}, {@link FCPlayerInventory}) route their nested
 * pieces (slot {@code ItemStack}s, extra inventories) back through the shared type-aware mapper, so
 * registering the ItemStack adapter once is enough for the whole family to compose.
 */
public final class HyConfigTypes {

    private HyConfigTypes() {
    }

    public static void register() {
        registerVectors();
        registerLocation();
        registerItemStack();
        registerGenericInventory();
        registerFCPlayerInventory();
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
        ConfigFactory.register(Vector3d.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Vector3d.class, node -> new Vector3d(
                        node.get("x").asDouble(), node.get("y").asDouble(), node.get("z").asDouble()))
        );

        ConfigFactory.register(Vector3i.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Vector3i.class, node -> new Vector3i(
                        node.get("x").asInt(), node.get("y").asInt(), node.get("z").asInt()))
        );

        ConfigFactory.register(Vector3f.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Vector3f.class, node -> new Vector3f(
                        (float) node.get("x").asDouble(), (float) node.get("y").asDouble(),
                        (float) node.get("z").asDouble()))
        );

        ConfigFactory.register(Rotation3f.class).jackson(
                mapSerializer(v -> vec3(v.x(), v.y(), v.z())),
                objectDeserializer(Rotation3f.class, node -> new Rotation3f(
                        (float) node.get("x").asDouble(), (float) node.get("y").asDouble(),
                        (float) node.get("z").asDouble()))
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
        );
    }

    private static Map<String, Object> vec3(Object x, Object y, Object z) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        return map;
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
        );
    }

    /** Parse the compact string form {@code WORLD | x y z xRot yRot zRot}. */
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

    // ==================== GenericInventory ====================

    /** A {@code {<slot>: ItemStack}} object; each slot value routes through the registered ItemStack adapter. */
    private static void registerGenericInventory() {
        ConfigFactory.register(GenericInventory.class).jackson(
                new JsonSerializer<GenericInventory>() {
                    @Override
                    public void serialize(GenericInventory value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        gen.writeStartObject();
                        for (ItemInSlot itemInSlot : value.getItems()) {
                            gen.writeFieldName(String.valueOf(itemInSlot.getSlot()));
                            gen.writeObject(itemInSlot.getItemStack());
                        }
                        gen.writeEndObject();
                    }
                },
                new StdDeserializer<GenericInventory>(GenericInventory.class) {
                    @Override
                    public GenericInventory deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return readGenericInventory(parser.readValueAsTree(), context);
                    }
                }
        );
    }

    private static GenericInventory readGenericInventory(JsonNode node, DeserializationContext context)
            throws IOException {
        List<ItemInSlot> items = new ArrayList<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    ItemStack itemStack = context.readTreeAsValue(entry.getValue(), ItemStack.class);
                    items.add(new ItemInSlot(slot, itemStack));
                } catch (Exception e) {
                    EverNifeCore.getLog().info("Failed to load ItemSlot from [" + entry + "]");
                    e.printStackTrace();
                }
            });
        }
        return new GenericInventory(items);
    }

    // ==================== FCPlayerInventory ====================

    /** An object with the six {@link GenericInventory} sub-inventories (storage/armor/hotbar/utility/tools/
     *  backpack) and one nested inventory per registered extra factory under {@code extra.<id>}. */
    private static void registerFCPlayerInventory() {
        ConfigFactory.register(FCPlayerInventory.class).jackson(
                new JsonSerializer<FCPlayerInventory>() {
                    @Override
                    public void serialize(FCPlayerInventory value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        gen.writeStartObject();
                        gen.writeFieldName("storage");
                        gen.writeObject(value.getStorage());
                        gen.writeFieldName("armor");
                        gen.writeObject(value.getArmor());
                        gen.writeFieldName("hotbar");
                        gen.writeObject(value.getHotbar());
                        gen.writeFieldName("utility");
                        gen.writeObject(value.getUtility());
                        gen.writeFieldName("tools");
                        gen.writeObject(value.getTools());
                        gen.writeFieldName("backpack");
                        gen.writeObject(value.getBackpack());

                        gen.writeObjectFieldStart("extra");
                        for (ExtraInv extraInv : value.getExtraInvs()) {
                            gen.writeFieldName(extraInv.getFactory().getId());
                            gen.writeObject((GenericInventory) extraInv);
                        }
                        gen.writeEndObject();

                        gen.writeEndObject();
                    }
                },
                new StdDeserializer<FCPlayerInventory>(FCPlayerInventory.class) {
                    @Override
                    public FCPlayerInventory deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return readFCPlayerInventory(parser.readValueAsTree(), context);
                    }
                }
        );
    }

    private static FCPlayerInventory readFCPlayerInventory(JsonNode node, DeserializationContext context)
            throws IOException {
        GenericInventory storage = readChildAsInventory(node, "storage", context);
        GenericInventory armor = readChildAsInventory(node, "armor", context);
        GenericInventory hotbar = readChildAsInventory(node, "hotbar", context);
        GenericInventory utility = readChildAsInventory(node, "utility", context);
        GenericInventory tools = readChildAsInventory(node, "tools", context);
        GenericInventory backpack = readChildAsInventory(node, "backpack", context);

        List<ExtraInv> extraInvList = new ArrayList<>();
        JsonNode extraNode = node == null ? null : node.get("extra");
        if (extraNode != null && extraNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : iterate(extraNode)) {
                String extraInvKey = entry.getKey();
                try {
                    IExtraInvFactory factory = ExtraInvManager.getFactory(extraInvKey);
                    if (factory == null) {
                        continue;
                    }
                    GenericInventory extraContent = context.readTreeAsValue(entry.getValue(), GenericInventory.class);
                    extraInvList.add(new ExtraInv(factory, extraContent.getItems()));
                } catch (Throwable e) {
                    EverNifeCore.getLog().info("Failed to load ExtraInv(" + extraInvKey + ") at " + entry.getValue());
                    e.printStackTrace();
                }
            }
        }

        return new FCPlayerInventory(storage, armor, hotbar, utility, tools, backpack, extraInvList);
    }

    private static GenericInventory readChildAsInventory(JsonNode node, String field, DeserializationContext context)
            throws IOException {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull()) {
            return new GenericInventory();
        }
        return context.readTreeAsValue(child, GenericInventory.class);
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterate(JsonNode node) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        node.fields().forEachRemaining(entries::add);
        return entries;
    }

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
