package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventorySchema;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemDescription;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.everylibs.util.FCInputReader;
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
import java.util.Arrays;
import java.util.Iterator;
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
 * <p><b>StoredInventory</b> persists as the versioned envelope of {@link StoredInventorySchema}, and reading
 * one runs the registered schema migrations first - which is what lets a file written as a bare slot map, the
 * only shape that existed before, still load.
 *
 * <p>The {@code GenericInventory}/{@code FCPlayerInventory} families are NOT registered here: they self-describe
 * as bound entities ({@code @JsonAnyGetter} + {@code ConfigLifecycle}), so nesting composes for free. The
 * config-value-shaped {@link InvItem} still routes its nested pieces back through the shared type-aware
 * mapper.
 */
public final class McConfigTypes {

    private McConfigTypes() {
    }

    /** Register the Bukkit config types into {@link ConfigFactory}. Meant to run once at bootstrap; idempotent
     *  registration is guaranteed by the caller (the platform guard). */
    public static void register() {
        registerLocation();
        registerItemStack();
        registerSlotSet();
        registerStoredInventory();
    }

    // ==================== SlotSet ====================

    /**
     * The single slot-list codec. On disk a slot list has been written three ways - the bracketed string
     * {@code "[1,2,3]"}, a YAML list, and a bare number for one slot - and each of them used to be parsed
     * somewhere else, with the bare number silently degrading to "no slots". All three are read here and
     * only the bracketed string is ever written back.
     *
     * <p>An empty list means "nowhere", which is how an admin switches an icon off, and is never an error.
     * Text that is neither of the three IS an error, and it is thrown naming the offending value: only the
     * caller knows which field of which file holds it, and "deliberately switched off" is what an empty
     * list means - a broken one has to read as broken.</p>
     */
    private static void registerSlotSet() {
        ConfigFactory.register(SlotSet.class).jackson(
                new JsonSerializer<SlotSet>() {
                    @Override
                    public void serialize(SlotSet value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        gen.writeString(value.serialize());
                    }
                },
                new StdDeserializer<SlotSet>(SlotSet.class) {
                    @Override
                    public SlotSet deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return readSlotSet(parser.readValueAsTree());
                    }
                }
        );
    }

    private static SlotSet readSlotSet(JsonNode node) {
        if (node == null || node.isNull()) {
            return SlotSet.EMPTY;
        }
        if (node.isArray()) {
            List<Integer> slots = new ArrayList<>();
            for (JsonNode element : node) {
                for (int slot : SlotSet.parse(element.asText()).toArray()) {
                    slots.add(slot);
                }
            }
            int[] values = new int[slots.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = slots.get(i);
            }
            return SlotSet.of(values);
        }
        if (node.isNumber()) {
            return SlotSet.of(node.asInt());
        }
        return SlotSet.parse(node.asText());
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
        if (itemStack == null) {
            //an empty slot has no item-data lines to write, and what it reads back as is null again
            gen.writeNull();
            return;
        }
        ItemDescription description = ItemEngine.get().read(itemStack);
        if (!description.isComplete() && ItemEngine.get().getRuntime().isLive()) {
            //saving is the moment data is lost for good, so an incomplete read cannot pass quietly
            EverNifeCore.getLog().warning("Saving an item this server could not fully read ("
                    + itemStack.getType() + "): " + description.describeGaps()
                    + ". What is missing will not be in the file. Fix the gap before saving again, or the "
                    + "next load will bring back a smaller item than the one you had.");
        }
        gen.writeObject(description.getLines());
    }

    /**
     * READ, tolerant across every historical on-disk shape, in the same precedence the legacy loader used.
     */
    private static ItemStack readItemStack(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

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

    // ==================== StoredInventory ====================

    /**
     * The versioned envelope of {@link StoredInventorySchema}. Unlike {@code GenericInventory}, this one is
     * NOT self-describing: what it stores has to carry the number of the schema it was written with, and the
     * read side has to run the registered migrations before binding anything.
     *
     * <p>Every stack inside routes through the {@code ItemStack} pair above, so an inventory written here
     * reads the same on-disk shapes an item written anywhere else does.</p>
     */
    private static void registerStoredInventory() {
        ConfigFactory.register(StoredInventory.class).jackson(
                new JsonSerializer<StoredInventory>() {
                    @Override
                    public void serialize(StoredInventory value, JsonGenerator gen, SerializerProvider provider)
                            throws IOException {
                        writeStoredInventory(value, gen);
                    }
                },
                new StdDeserializer<StoredInventory>(StoredInventory.class) {
                    @Override
                    public StoredInventory deserialize(JsonParser parser, DeserializationContext context)
                            throws IOException {
                        return readStoredInventory(parser.readValueAsTree());
                    }
                }
        );
    }

    /**
     * WRITE. Everything comes from one {@link StoredInventory#snapshotForWrite() snapshot}: saving runs
     * on a worker thread, and an envelope whose size, items and maximums were read at three different
     * moments describes an inventory that never existed.
     */
    private static void writeStoredInventory(StoredInventory inventory, JsonGenerator gen) throws IOException {
        StoredInventory.Snapshot snapshot = inventory.snapshotForWrite();
        gen.writeStartObject();
        gen.writeNumberField(StoredInventorySchema.VERSION_KEY, StoredInventorySchema.VERSION);
        gen.writeNumberField(StoredInventorySchema.SIZE_KEY, snapshot.getCapacity());

        gen.writeObjectFieldStart(StoredInventorySchema.ITEMS_KEY);
        for (int slot = 0; slot < snapshot.getCapacity(); slot++) {
            ItemStack item = snapshot.getItem(slot);
            if (item != null) {
                gen.writeFieldName(String.valueOf(slot));
                writeItemStack(item, gen);
            }
        }
        gen.writeEndObject();

        Map<String, Integer> declaredMaximums = new LinkedHashMap<>();
        for (int slot = 0; slot < snapshot.getCapacity(); slot++) {
            int max = snapshot.getMaxStackSize(slot);
            if (max != StoredInventory.ITEM_DEFAULT) {
                declaredMaximums.put(String.valueOf(slot), max);
            }
        }
        if (!declaredMaximums.isEmpty()) {
            gen.writeObjectFieldStart(StoredInventorySchema.MAX_STACK_SIZES_KEY);
            for (Map.Entry<String, Integer> entry : declaredMaximums.entrySet()) {
                gen.writeNumberField(entry.getKey(), entry.getValue());
            }
            gen.writeEndObject();
        }
        gen.writeEndObject();
    }

    private static StoredInventory readStoredInventory(JsonNode stored) {
        if (stored == null || !stored.isObject()) {
            return null;
        }
        ObjectNode envelope = StoredInventorySchema.migrate((ObjectNode) stored);
        JsonNode items = envelope.get(StoredInventorySchema.ITEMS_KEY);
        JsonNode size = envelope.get(StoredInventorySchema.SIZE_KEY);

        //storage answers on a worker thread, and an inventory being rebuilt is nobody's until it is
        //returned - StoredInventory.Restoring is the door that says so
        StoredInventory.Restoring restoring =
                StoredInventory.restoring(Math.max(1, size == null ? 0 : size.asInt()));
        if (items != null && items.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> fields = items.fields(); fields.hasNext(); ) {
                Map.Entry<String, JsonNode> field = fields.next();
                int slot = StoredInventorySchema.slotNumberOf(field.getKey());
                if (slot >= 0 && slot < restoring.getCapacity()) {
                    restoring.setItem(slot, readItemStack(field.getValue()));
                }
            }
        }

        JsonNode maximums = envelope.get(StoredInventorySchema.MAX_STACK_SIZES_KEY);
        if (maximums != null && maximums.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> fields = maximums.fields(); fields.hasNext(); ) {
                Map.Entry<String, JsonNode> field = fields.next();
                int slot = StoredInventorySchema.slotNumberOf(field.getKey());
                if (slot >= 0 && slot < restoring.getCapacity()) {
                    restoring.setMaxStackSize(slot, field.getValue().asInt());
                }
            }
        }
        return restoring.build();
    }

    // GenericInventory and FCPlayerInventory are self-describing bound entities (see their @JsonAnyGetter /
    // ConfigLifecycle) - they need no central registration here.

    // ==================== shared bridge ====================

    /**
     * Materialize {@code node} into a file-less, type-aware {@link ConfigSection}. This is the bridge a
     * path-based reader ({@link InvItem#onConfigLoad}) needs: it reads through
     * {@code getValue(path, ItemStack.class)} etc., which the in-memory config resolves through the
     * same registered adapters. Delegates to the shared {@link ConfigFactory#inMemorySection(JsonNode)}.
     */
    private static ConfigSection sectionFrom(JsonNode node) {
        return ConfigFactory.inMemorySection(node);
    }
}
