package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.everyconfig.config.Config;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored form: what a save writes, what a load of an older file reads, and what happens to a file
 * nothing knows how to read.
 *
 * <p>The old-shape fixtures below are typed out by hand rather than produced by today's writer. A round
 * trip through one's own code proves only that the two halves agree with each other today; a file
 * written out is read years later by a version that has changed both, and the only thing that can stand
 * in for those years is bytes nobody generated.</p>
 */
class StoredInventorySchemaTest {

    /** A file written before the envelope existed: the bare slot map every inventory used to be. */
    private static final String VERSION_1_BYTES = String.join("\n",
            "backpack:",
            "  '0':",
            "  - 'type: DIAMOND'",
            "  - 'amount: 5'",
            "  '3':",
            "  - 'type: DIRT'",
            "");

    private static final AtomicInteger UNIQUE_FILE = new AtomicInteger();

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private Path fileHolding(String contents) throws IOException {
        Path file = newFile();
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private Path newFile() {
        return tempDir.resolve("storage-" + UNIQUE_FILE.incrementAndGet() + ".yml");
    }

    private static StoredInventory read(Path file) {
        return ConfigFactory.open(file).getValue("backpack", StoredInventory.class);
    }

    private static void write(Path file, StoredInventory inventory) {
        Config config = ConfigFactory.open(file);
        config.setValue("backpack", inventory);
        config.save();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a save writes
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void whatIsWrittenSaysWhichSchemaWroteIt() {
        Path file = newFile();
        write(file, new StoredInventory(9));

        Config written = ConfigFactory.open(file);
        assertEquals(StoredInventorySchema.VERSION, written.getValue("backpack.version", Integer.class).intValue(),
                "a file with no version in it is a file that can only ever be read one way");
        assertEquals(9, written.getValue("backpack.size", Integer.class).intValue());
    }

    @Test
    void anInventorySurvivesBeingWrittenAndReadBack() {
        StoredInventory saved = new StoredInventory(9);
        saved.setItemSilently(0, new ItemStack(Material.DIAMOND, 5));
        saved.setItemSilently(8, new ItemStack(Material.DIRT, 2));
        saved.setMaxStackSize(4, 16);

        Path file = newFile();
        write(file, saved);
        StoredInventory reloaded = read(file);

        assertNotNull(reloaded);
        assertEquals(9, reloaded.getSize());
        assertEquals(Material.DIAMOND, reloaded.getItem(0).getType());
        assertEquals(5, reloaded.getItem(0).getAmount());
        assertEquals(Material.DIRT, reloaded.getItem(8).getType());
        assertEquals(2, reloaded.getItem(8).getAmount());
        assertNull(reloaded.getItem(1), "a slot nothing was in stays empty");
        assertEquals(16, reloaded.getMaxStackSize(4), "and a slot that holds less than the item says so");
        assertEquals(StoredInventory.ITEM_DEFAULT, reloaded.getMaxStackSize(0));
    }

    @Test
    void anInventoryCrossesToStorageThroughTheSameTypeAuthority() {
        VaultedItems saved = new VaultedItems();
        saved.vault.setItemSilently(2, new ItemStack(Material.DIAMOND, 4));
        saved.vault.setMaxStackSize(0, 1);

        ConfigFactoryCodec<VaultedItems> codec = ConfigFactoryCodec.json(VaultedItems.class);
        VaultedItems read = codec.decode(codec.encode(saved));

        assertEquals(9, read.vault.getSize(), "a storage backend writes bytes, not a config file, and the "
                + "envelope has to survive that road too - it is the one a plugin persists through");
        assertEquals(4, read.vault.getItem(2).getAmount());
        assertEquals(1, read.vault.getMaxStackSize(0));
    }

    /** A persisted entity with an inventory in it, which is the shape a plugin actually stores. */
    public static class VaultedItems {

        public StoredInventory vault = new StoredInventory(9);

        public VaultedItems() {                               //Jackson

        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a load of an older file reads
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aFileFromBeforeTheEnvelopeIsReadThroughTheMigration() throws IOException {
        StoredInventory loaded = read(fileHolding(VERSION_1_BYTES));

        assertNotNull(loaded, "the shape every inventory in this project used to be stored in");
        assertEquals(Material.DIAMOND, loaded.getItem(0).getType());
        assertEquals(5, loaded.getItem(0).getAmount());
        assertEquals(Material.DIRT, loaded.getItem(3).getType());
        assertEquals(4, loaded.getSize(), "the old shape never said how big it was, so it ends where its "
                + "items do - slot 3 is the last one, which makes four");
    }

    @Test
    void aMigratedFileIsWrittenBackInTheCurrentShape() throws IOException {
        StoredInventory loaded = read(fileHolding(VERSION_1_BYTES));

        Path rewritten = newFile();
        write(rewritten, loaded);

        assertEquals(StoredInventorySchema.VERSION,
                ConfigFactory.open(rewritten).getValue("backpack.version", Integer.class).intValue(),
                "reading an old file once is how it stops being an old file");
        assertEquals(5, read(rewritten).getItem(0).getAmount());
    }

    @Test
    void migrationsChainOneVersionAtATime() throws IOException {
        //a version that never existed, registered here to prove the chain runs more than one step
        StoredInventorySchema.registerMigration(0, older -> {
            older.put(StoredInventorySchema.VERSION_KEY, StoredInventorySchema.LEGACY_SLOT_MAP_VERSION);
            return older;
        });

        StoredInventory loaded = read(fileHolding(String.join("\n",
                "backpack:",
                "  version: 0",
                "  '2':",
                "  - 'type: DIAMOND'",
                "  - 'amount: 7'",
                "")));

        assertNotNull(loaded);
        assertEquals(7, loaded.getItem(2).getAmount(), "zero became one, and one became the shape of today");
        assertEquals(3, loaded.getSize());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What is refused
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aVersionNothingKnowsHowToReadIsRefusedInsteadOfGuessedAt() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> StoredInventorySchema.migrate(envelope(StoredInventorySchema.VERSION - 1000)));

        assertTrue(failure.getMessage().contains("registerMigration"), "the complaint says what would fix "
                + "it: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("throw its contents away"), failure.getMessage());
    }

    @Test
    void aFileFromANewerVersionIsRefusedRatherThanReadShort() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> StoredInventorySchema.migrate(envelope(StoredInventorySchema.VERSION + 1)));

        assertTrue(failure.getMessage().contains("Update the plugin"), failure.getMessage());
    }

    @Test
    void aMigrationThatDoesNotRaiseTheVersionIsCalledOutInsteadOfRunForever() {
        int stuckVersion = -7;
        StoredInventorySchema.registerMigration(stuckVersion, unchanged -> unchanged);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> StoredInventorySchema.migrate(envelope(stuckVersion)));

        assertTrue(failure.getMessage().contains("run it forever"), failure.getMessage());
    }

    @Test
    void anInventoryTheReadRefusesIsSaidOutLoudInsteadOfComingBackEmpty() {
        byte[] unreadable = ("{\"vault\":{\"version\":" + (StoredInventorySchema.VERSION + 1000)
                + ",\"size\":9,\"items\":{}}}").getBytes(StandardCharsets.UTF_8);

        ConfigFactoryCodec<VaultedItems> codec = ConfigFactoryCodec.json(VaultedItems.class);
        AtomicReference<VaultedItems> read = new AtomicReference<>();
        List<String> logged = Logs.capture(() -> read.set(codec.decode(unreadable)));

        assertTrue(read.get().vault.isEmpty(), "the entity still loads and the field keeps the default, "
                + "which is exactly what an inventory nobody ever filled looks like");
        assertTrue(anyContains(logged, "VaultedItems"), logged.toString());
        assertTrue(anyContains(logged, "vault"), "the report names the field that was lost: " + logged);
        assertTrue(anyContains(logged, "Update the plugin"), "and why it was: " + logged);
        assertTrue(anyContains(logged, "next save"), "and what the loss costs if nothing is done: " + logged);
    }

    private static boolean anyContains(List<String> logged, String fragment) {
        for (String line : logged) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aFileWithNoVersionInItIsTheOldestShapeThereIs() {
        assertEquals(StoredInventorySchema.LEGACY_SLOT_MAP_VERSION,
                StoredInventorySchema.versionOf(JsonNodeFactory.instance.objectNode()));
        assertEquals(4, StoredInventorySchema.versionOf(envelope(4)));
    }

    @Test
    void aVersionThatIsNotANumberIsRefusedRatherThanReadAsTheOldestShape() {
        ObjectNode broken = JsonNodeFactory.instance.objectNode();
        broken.put(StoredInventorySchema.VERSION_KEY, "two");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> StoredInventorySchema.versionOf(broken));

        assertTrue(failure.getMessage().contains("keep nothing of it"), failure.getMessage());

        ObjectNode quoted = JsonNodeFactory.instance.objectNode();
        quoted.put(StoredInventorySchema.VERSION_KEY, String.valueOf(StoredInventorySchema.VERSION));
        assertEquals(StoredInventorySchema.VERSION, StoredInventorySchema.versionOf(quoted),
                "a number somebody typed in quotes is still that number");
    }

    private static ObjectNode envelope(int version) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(StoredInventorySchema.VERSION_KEY, version);
        return node;
    }

}
