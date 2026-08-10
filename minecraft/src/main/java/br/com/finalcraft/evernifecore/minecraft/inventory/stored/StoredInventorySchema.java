package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
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
 *   '0': [ 'type:DIAMOND', 'amount:5' ]
 *   '4': [ 'type:DIRT' ]
 * maxStackSizes:
 *   '0': 16
 * }</pre>
 *
 * <p>Version 1 is the shape this project stored every inventory in before the envelope existed: a bare
 * {@link GenericInventory} slot map, {@code {'0': item, '4': item}}, with no version in it. A file with
 * no {@code version} key IS a version 1 file - there is no other way to read one, and no file will ever
 * be written that way again.</p>
 *
 * <p>A change to the shape gets a new number and a function that turns the previous one into it. The
 * one below is the migration this class registers for the shape that came before the envelope, and it
 * is what any of them looks like: it is handed the whole stored object and answers the whole stored
 * object, with its version raised to one this reader knows how to read.</p>
 *
 * <pre>{@code
 * StoredInventorySchema.registerMigration(1, stored -> {
 *     ObjectNode envelope = ...;   // whatever the new version says that the old one did not
 *     envelope.put("version", 2);
 *     return envelope;
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
                    + "function to run: it is what carries a file written with that version up to the next "
                    + "one. A version with nothing to change still needs one - a function that raises '"
                    + VERSION_KEY + "' and answers what it was given - because a version with no way up is "
                    + "refused, and every file still on version " + fromVersion + " with it.");
        }
        MIGRATIONS.put(fromVersion, migration);
    }

    /**
     * Drops every migration a caller registered, leaving the ones this class declares itself.
     *
     * <p>The table is process-wide and nothing removes from it, so a test that registers one leaves it
     * registered for whatever runs next in the same JVM.</p>
     */
    public static void forgetRegisteredMigrations() {
        MIGRATIONS.clear();
        registerMigration(LEGACY_SLOT_MAP_VERSION, StoredInventorySchema::slotMapToEnvelope);
    }

    /**
     * The version {@code stored} was written with. A file with no version at all is a bare slot map -
     * that is the only thing it can be. A file whose version is there but unreadable is neither, and
     * reading it as the oldest shape would quietly empty it, so it is refused.
     */
    public static int versionOf(@Nonnull JsonNode stored) {
        Objects.requireNonNull(stored, "A schema version was asked of nothing at all. Whether a stored "
                + "inventory exists is the caller's own question: an absent one has no version, and "
                + "answering the oldest there is would rebuild it empty over whatever is really there.");
        JsonNode version = stored.get(VERSION_KEY);
        if (version == null || version.isNull()) {
            return LEGACY_SLOT_MAP_VERSION;
        }
        if (version.canConvertToInt()) {
            return version.asInt();
        }
        try {
            //a version is any whole number, negatives included - migrate() is what refuses the ones with
            //no way up, and it names the version it was actually given
            return Integer.parseInt(version.asText().trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalStateException("A stored inventory says it was written with schema version ["
                    + version.asText() + "], which is not a number. Put the number back - reading the file "
                    + "as if it had no version at all would read it as the oldest shape there is and keep "
                    + "nothing of it.");
        }
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
