package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.ExtraInvManager;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.inventory.player.FCPlayerInventory;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.ComparableItem;
import br.com.finalcraft.evernifecore.minecraft.itemstack.ComparableItemComplex;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Bukkit platform's config types for {@link ConfigFactory}. Ported from the legacy {@code @Loadable}/
 * {@code Salvable} registrations so that config files written by the old engine keep reading correctly under
 * the Jackson engine.
 *
 * <p><b>Location</b> persists in two shapes on disk: a MAP {@code {worldName, x, y, z, yaw, pitch}} and a
 * compact STRING {@code WORLD | x y z yaw pitch}. The deserializer reads both; new writes emit the MAP form.
 *
 * <p><b>ItemStack</b> historically persisted in several shapes:
 * <ul>
 *   <li>a custom-container object {@code {invItem:{name:...}, ...}} for a registered {@link InvItem};</li>
 *   <li>a legacy object {@code {minecraftIdentifier:..., nbt:[...]}} (files from an early on-disk format);</li>
 *   <li>an item-data string-list (the {@link ItemDataPart} form); or</li>
 *   <li>a bare Bukkit/Minecraft identifier string.</li>
 * </ul>
 * The deserializer reads every shape. The serializer always writes the item-data string-list form - see the
 * note on {@link #writeItemStack} for the one intentional write-shape narrowing versus the legacy engine.
 *
 * <p><b>Inventory families</b> ({@link GenericInventory}, {@link FCPlayerInventory}) and the
 * config-value-shaped {@link InvItem} and {@link LayoutIcon} route their nested pieces (slot
 * {@code ItemStack}s, extra inventories) back through the shared type-aware mapper, so registering the
 * ItemStack adapter once is enough for the whole family to compose.
 */
public final class McConfigTypes {

    private McConfigTypes() {
    }

    /** Register the Bukkit config types into {@link ConfigFactory}. Meant to run once at bootstrap; idempotent
     *  registration is guaranteed by the caller (the platform guard). */
    public static void register() {
        registerLocation();
        registerItemStack();
        registerGenericInventory();
        registerFCPlayerInventory();
        registerLayoutIcon();
        registerComparableItems();
    }

    // ==================== Location ====================

    private static void registerLocation() {
        ConfigFactory.register(Location.class).jackson(
                new JsonSerializer<Location>() {
                    @Override
                    public void serialize(Location value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("worldName", value.getWorld().getName());
                        map.put("x", value.getX());
                        map.put("y", value.getY());
                        map.put("z", value.getZ());
                        map.put("yaw", value.getYaw());
                        map.put("pitch", value.getPitch());
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
                        return new Location(
                                Bukkit.getWorld(stringOrNull(node.get("worldName"))),
                                node.get("x").asDouble(),
                                node.get("y").asDouble(),
                                node.get("z").asDouble(),
                                (float) node.get("yaw").asDouble(),
                                (float) node.get("pitch").asDouble()
                        );
                    }
                }
        );
    }

    /** Parse the compact string form {@code WORLD | x y z yaw pitch}. */
    private static Location fromLegacyString(String serializedLocation) {
        String[] split = serializedLocation.split(Pattern.quote("|"));
        String[] splitCoords = split[1].split(" ");

        World world = Bukkit.getWorld(split[0].trim());
        Double x = FCInputReader.parseDouble(splitCoords[0]);
        Double y = FCInputReader.parseDouble(splitCoords[1]);
        Double z = FCInputReader.parseDouble(splitCoords[2]);
        Double yaw = FCInputReader.parseDouble(splitCoords[3]);
        Double pitch = FCInputReader.parseDouble(splitCoords[4]);

        return new Location(world, x, y, z, yaw.floatValue(), pitch.floatValue());
    }

    private static String stringOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    // ==================== ItemStack ====================

    private static void registerItemStack() {
        // Registered against the base ItemStack.class. A base-class Jackson serializer applies to subclasses
        // too (e.g. CraftItemStack), matching the legacy setAllowExtends(true).
        ConfigFactory.register(ItemStack.class).jackson(
                new JsonSerializer<ItemStack>() {
                    @Override
                    public void serialize(ItemStack value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        writeItemStack(value, gen);
                    }
                },
                new StdDeserializer<ItemStack>(ItemStack.class) {
                    @Override
                    public ItemStack deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return readItemStack(parser.readValueAsTree());
                    }
                }
        );
    }

    /**
     * WRITE. Always emits the item-data string-list ({@link ItemDataPart}) form.
     *
     * <p><b>Write-shape narrowing:</b> the legacy engine, for a type backed by a registered {@link InvItem},
     * wrote a custom-container object ({@code {invItem:{name:...}, ...}}) driven by {@code InvItem.onConfigSave}.
     * That hook writes into a {@code ConfigSection}, which the Jackson streaming path cannot host, so we do
     * not reproduce that write shape. READ of the InvItem object shape is still fully supported, so older
     * files remain readable; only re-saves collapse an InvItem to its item-data list form.
     */
    private static void writeItemStack(ItemStack itemStack, JsonGenerator gen) throws IOException {
        List<String> itemData = ItemDataPart.readItem(itemStack);
        gen.writeObject(itemData);
    }

    /**
     * READ, tolerant across every historical on-disk shape, in the same precedence the legacy loader used.
     */
    private static ItemStack readItemStack(JsonNode node) {
        // 1. Custom-container object: {invItem:{name:...}, ...}
        JsonNode invItemNode = node.isObject() ? node.get("invItem") : null;
        if (invItemNode != null && invItemNode.get("name") != null) {
            String invItemName = invItemNode.get("name").asText();
            InvItem invItem = InvItemManager.of(invItemName);
            if (invItem == null) {
                EverNifeCore.getLog().warning("Found an InvItem [%s] on a config value that doesn't exists! The content will be ignored!", invItemName);
                return null;
            }
            return invItem.onConfigLoad(sectionFrom(node));
        }

        // 2. Legacy object with a Minecraft identifier (files from an early on-disk format).
        if (node.isObject() && node.has("minecraftIdentifier")) {
            String minecraftIdentifier = node.get("minecraftIdentifier").asText();
            JsonNode nbtNode = node.get("nbt");
            if (nbtNode != null && !nbtNode.isNull()) {
                StringBuilder nbt = new StringBuilder();
                if (nbtNode.isArray()) {
                    for (JsonNode element : nbtNode) {
                        nbt.append(element.asText());
                    }
                } else {
                    nbt.append(nbtNode.asText());
                }
                return FCItemFactory.from(minecraftIdentifier + " " + nbt).build();
            }
            return FCItemUtils.fromMinecraftIdentifier(minecraftIdentifier);
        }

        // 3. Item-data string-list (the ItemDataPart form).
        if (node.isArray()) {
            List<String> itemData = new ArrayList<>();
            for (JsonNode element : node) {
                itemData.add(element.asText());
            }
            return FCItemFactory.from(itemData).build();
        }

        // 4. A bare Bukkit ("MINECRAFT_STONE") or Minecraft ("minecraft:stone") identifier string.
        return FCItemFactory.from(node.asText()).build();
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

    /** An object with the four armor {@code ItemStack}s, the main {@link GenericInventory}, and one nested
     *  inventory per registered extra factory under {@code extra.<id>}. */
    private static void registerFCPlayerInventory() {
        ConfigFactory.register(FCPlayerInventory.class).jackson(
                new JsonSerializer<FCPlayerInventory>() {
                    @Override
                    public void serialize(FCPlayerInventory value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        gen.writeStartObject();
                        gen.writeFieldName("helmet");
                        gen.writeObject(value.getHelmet());
                        gen.writeFieldName("chestplate");
                        gen.writeObject(value.getChestplate());
                        gen.writeFieldName("leggings");
                        gen.writeObject(value.getLeggings());
                        gen.writeFieldName("boots");
                        gen.writeObject(value.getBoots());
                        gen.writeFieldName("inventory");
                        gen.writeObject(value.getInventory());

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
        ItemStack helmet = readChildAs(node, "helmet", ItemStack.class, context);
        ItemStack chestplate = readChildAs(node, "chestplate", ItemStack.class, context);
        ItemStack leggings = readChildAs(node, "leggings", ItemStack.class, context);
        ItemStack boots = readChildAs(node, "boots", ItemStack.class, context);

        GenericInventory inventory = readChildAs(node, "inventory", GenericInventory.class, context);
        if (inventory == null) {
            inventory = new GenericInventory();
        }

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

        return new FCPlayerInventory(helmet, chestplate, leggings, boots, inventory, extraInvList);
    }

    private static <T> T readChildAs(JsonNode node, String field, Class<T> type, DeserializationContext context)
            throws IOException {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return context.readTreeAsValue(child, type);
    }

    // ==================== LayoutIcon ====================

    /** Read-shaped ({@code {Slot, Permission, DisplayItem}}); its path-based read logic is reused via an
     *  in-memory type-aware bridge so it stays identical to the config-driven loader. */
    private static void registerLayoutIcon() {
        ConfigFactory.register(LayoutIcon.class).jackson(
                new JsonSerializer<LayoutIcon>() {
                    @Override
                    public void serialize(LayoutIcon value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        Map<String, Object> map = new LinkedHashMap<>();
                        List<String> slots = new ArrayList<>();
                        for (int slot : value.getSlot()) {
                            slots.add(String.valueOf(slot));
                        }
                        map.put("Slot", slots);
                        map.put("Permission", value.getPermission());
                        if (value.getItemStack() != null) {
                            map.put("DisplayItem", FCItemFactory.from(value.getItemStack()).toDataPart());
                        }
                        gen.writeObject(map);
                    }
                },
                new StdDeserializer<LayoutIcon>(LayoutIcon.class) {
                    @Override
                    public LayoutIcon deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return LayoutIcon.onConfigLoad(sectionFrom(parser.readValueAsTree()));
                    }
                }
        );
    }

    // ==================== ComparableItem / ComparableItemComplex ====================

    /** Both persist as their compact material/damage string. */
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

    // ==================== shared bridge ====================

    /**
     * Materialize {@code node} into a file-less, type-aware {@link Config} and return a root
     * {@link ConfigSection} over it. This is the bridge a path-based reader ({@link InvItem#onConfigLoad},
     * {@link LayoutIcon#onConfigLoad}) needs: it reads through {@code getValue(path, ItemStack.class)} etc.,
     * which the in-memory config resolves through the same registered adapters.
     */
    private static ConfigSection sectionFrom(JsonNode node) {
        Config bridge = ConfigFactory.inMemory();
        if (node != null && node.isObject()) {
            bridge.getRoot().setAll((ObjectNode) node);
        }
        return new ConfigSection(bridge, "");
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterate(JsonNode node) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        node.fields().forEachRemaining(entries::add);
        return entries;
    }
}
