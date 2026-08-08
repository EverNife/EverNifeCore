package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * The shape a {@link StoredInventory} is stored in, the number that names it, and the functions that
 * carry an older file up to it.
 *
 * <pre>{@code
 * version: 2
 * size: 27
 * items:
 *   '0': [ 'material: DIAMOND', 'amount: 5' ]
 *   '4': [ 'material: DIRT' ]
 * maxStackSizes:
 *   '0': 16
 * }</pre>
 *
 * <p>Version 1 is the shape this project stored every inventory in before the envelope existed: a bare
 * {@link GenericInventory} slot map, {@code {'0': item, '4': item}}, with no version in it. A file with
 * no {@code version} key IS a version 1 file - there is no other way to read one, and no file will ever
 * be written that way again.</p>
 *
 * <p>A change to the shape gets a new number and a function that turns the previous one into it:</p>
 *
 * <pre>{@code
 * StoredInventorySchema.registerMigration(2, stored -> {
 *     stored.put("version", 3);
 *     ... // whatever 3 says that 2 did not
 *     return stored;
 * });
 * }</pre>
 *
 * <p>Migrations chain: a version 1 file loaded by a server that knows version 3 runs 1 to 2 and then 2
 * to 3. A file whose version has no way up is refused loudly rather than read as something it is not -
 * reading it wrong is how a chest is emptied by an upgrade.</p>
 */
public final class StoredInventorySchema {

    /** The version everything is written with today. */
    public static final int VERSION = 2;

    /** The shape before the envelope: a bare slot map, recognised by having no version at all. */
    public static final int LEGACY_SLOT_MAP_VERSION = 1;

    public static final String VERSION_KEY = "version";
    public static final String SIZE_KEY = "size";
    public static final String ITEMS_KEY = "items";
    public static final String MAX_STACK_SIZES_KEY = "maxStackSizes";

    private static final Map<Integer, UnaryOperator<ObjectNode>> MIGRATIONS = new ConcurrentHashMap<>();

    static {
        registerMigration(LEGACY_SLOT_MAP_VERSION, StoredInventorySchema::slotMapToEnvelope);
    }

    private StoredInventorySchema() {

    }

    /**
     * Declares how a file written with {@code fromVersion} becomes one written with the next version.
     * The function is handed the whole stored object and answers the whole stored object, with its
     * {@code version} raised - anything else stops the chain where it started.
     */
    public static void registerMigration(int fromVersion, @Nonnull UnaryOperator<ObjectNode> migration) {
        if (migration == null) {
            throw new IllegalArgumentException("A schema migration from version " + fromVersion + " needs a "
                    + "function to run. Drop the call if there is nothing to change between the two versions.");
        }
        MIGRATIONS.put(fromVersion, migration);
    }

    /** The version {@code stored} was written with; a file with no version is a bare slot map. */
    public static int versionOf(@Nonnull JsonNode stored) {
        JsonNode version = stored == null ? null : stored.get(VERSION_KEY);
        return version == null || !version.isInt() ? LEGACY_SLOT_MAP_VERSION : version.asInt();
    }

    /**
     * Runs {@code stored} up to {@link #VERSION}, one registered migration at a time.
     *
     * @throws IllegalStateException when a version has no way up, or when a migration does not raise it
     */
    @Nonnull
    public static ObjectNode migrate(@Nonnull ObjectNode stored) {
        ObjectNode current = stored;
        int version = versionOf(current);
        while (version < VERSION) {
            UnaryOperator<ObjectNode> migration = MIGRATIONS.get(version);
            if (migration == null) {
                throw new IllegalStateException("A stored inventory stored with schema version " + version
                        + " cannot be read: nothing says how version " + version + " becomes version "
                        + (version + 1) + ". Register it with StoredInventorySchema.registerMigration("
                        + version + ", ...) before loading the file - reading it as if it were version "
                        + VERSION + " would throw its contents away.");
            }
            current = migration.apply(current);
            int migrated = versionOf(current);
            if (migrated <= version) {
                throw new IllegalStateException("The schema migration registered for version " + version
                        + " answered a file still on version " + migrated + ". A migration has to raise the "
                        + "'" + VERSION_KEY + "' of what it answers, or loading would run it forever.");
            }
            version = migrated;
        }
        if (version > VERSION) {
            throw new IllegalStateException("A stored inventory stored with schema version " + version
                    + " was written by a newer version of this plugin than the one reading it, which knows "
                    + "up to version " + VERSION + ". Update the plugin - an older reader cannot know what a "
                    + "newer file left out.");
        }
        return current;
    }

    /**
     * Version 1 to version 2: the slot map moves under {@code items} and the file learns how big it is.
     *
     * <p>The old shape never said how many slots the inventory had - it was a map, and a map ends where
     * its keys do - so the size becomes the last filled slot plus one. A region wider than that says so
     * when it opens, naming {@code setCapacity}.</p>
     */
    private static ObjectNode slotMapToEnvelope(ObjectNode slotMap) {
        ObjectNode envelope = JsonNodeFactory.instance.objectNode();
        ObjectNode items = JsonNodeFactory.instance.objectNode();
        int highest = -1;
        for (Iterator<Map.Entry<String, JsonNode>> fields = slotMap.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            int slot = slotNumberOf(field.getKey());
            if (slot < 0) {
                continue;
            }
            items.set(field.getKey(), field.getValue());
            highest = Math.max(highest, slot);
        }
        envelope.put(VERSION_KEY, LEGACY_SLOT_MAP_VERSION + 1);
        envelope.put(SIZE_KEY, highest + 1);
        envelope.set(ITEMS_KEY, items);
        return envelope;
    }

    /** A slot key as a number, or {@code -1} for a key that is not one. */
    public static int slotNumberOf(@Nonnull String key) {
        try {
            int slot = Integer.parseInt(key.trim());
            return slot < 0 ? -1 : slot;
        } catch (NumberFormatException notASlot) {
            return -1;
        }
    }

}
