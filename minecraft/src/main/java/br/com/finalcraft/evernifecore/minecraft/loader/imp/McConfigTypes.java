package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.everylibs.util.FCInputReader;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
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
 * <p><b>Location</b> persists as a MAP {@code {worldName, x, y, z, yaw, pitch}} for a solo value or field, and
 * as a compact STRING {@code WORLD|x y z yaw pitch} when it is a list element (via {@code asCompactElement}).
 * The deserializer reads both, plus the legacy string form.
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
 * <p>The {@code GenericInventory}/{@code FCPlayerInventory} families are NOT registered here: they self-describe
 * as bound entities ({@code @JsonAnyGetter} + {@code ConfigLifecycle}), so nesting composes for free. The
 * config-value-shaped {@link InvItem} and {@link LayoutIcon} still route their nested pieces back through the
 * shared type-aware mapper.
 */
public final class McConfigTypes {

    private McConfigTypes() {
    }

    /** Register the Bukkit config types into {@link ConfigFactory}. Meant to run once at bootstrap; idempotent
     *  registration is guaranteed by the caller (the platform guard). */
    public static void register() {
        registerLocation();
        registerItemStack();
        registerLayoutIcon();
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
        ).asCompactElement(McConfigTypes::compactLocation, McConfigTypes::fromLegacyString);
    }

    /** The compact list-element form {@code WORLD|x y z yaw pitch} - the exact inverse of
     *  {@link #fromLegacyString} (no space around the pipe, so the coord split stays clean). */
    private static String compactLocation(Location value) {
        return value.getWorld().getName() + "|"
                + value.getX() + " " + value.getY() + " " + value.getZ() + " "
                + value.getYaw() + " " + value.getPitch();
    }

    /** Parse the compact string form {@code WORLD|x y z yaw pitch}. */
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

    // GenericInventory and FCPlayerInventory are self-describing bound entities (see their @JsonAnyGetter /
    // ConfigLifecycle) - they need no central registration here.

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

    // ==================== shared bridge ====================

    /**
     * Materialize {@code node} into a file-less, type-aware {@link ConfigSection}. This is the bridge a
     * path-based reader ({@link InvItem#onConfigLoad}, {@link LayoutIcon#onConfigLoad}) needs: it reads
     * through {@code getValue(path, ItemStack.class)} etc., which the in-memory config resolves through the
     * same registered adapters. Delegates to the shared {@link ConfigFactory#inMemorySection(JsonNode)}.
     */
    private static ConfigSection sectionFrom(JsonNode node) {
        return ConfigFactory.inMemorySection(node);
    }
}
