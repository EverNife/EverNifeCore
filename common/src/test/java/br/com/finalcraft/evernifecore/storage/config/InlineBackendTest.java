package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inline single-backend reader a plugin uses to route its own data through a backend declared in
 * ITS OWN config (any file, any path), where the single child key IS the backend type and enabling is
 * implicit - the "declare one and use it directly" shape, distinct from storage.yml's "declare many,
 * pick by id".
 */
class InlineBackendTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @TempDir
    Path tempDir;

    private Config configOf(String content) throws IOException {
        File file = tempDir.resolve("inline_" + System.nanoTime() + ".yml").toFile();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return ConfigFactory.open(file);
    }

    private String path(String child) {
        return tempDir.resolve(child).toString().replace("\\", "/");
    }

    // ------------------------------------------------------------------
    // parse
    // ------------------------------------------------------------------

    @Test
    void parsesTheSingleInlineBackendKeyedByType() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  localfile:",
                "    path: \"" + path("snapshots") + "\"",
                "    format: json",
                ""));

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(config.getConfigSection("storage"));

        assertEquals(BackendType.LOCALFILE, backend.getType());
        assertEquals(BackendDefinition.FileFormat.JSON, backend.getFormat());
        assertTrue(backend.isEnabled(), "an inline backend is enabled just by being declared");
    }

    @Test
    void parsesAnInlineMongoBackendWithItsFields() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  mongo:",
                "    url: \"mongodb://localhost:27017\"",
                "    db: myplugin",
                ""));

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(config.getConfigSection("storage"));

        assertEquals(BackendType.MONGO, backend.getType());
        assertTrue(backend.isEnabled());
    }

    @Test
    void failsWhenNoBackendIsDeclared() throws IOException {
        Config config = configOf("storage: {}\n");
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parseInlineBackend(config.getConfigSection("storage")));
        assertTrue(error.getMessage().contains("No storage backend declared"), error.getMessage());
    }

    @Test
    void failsWhenTheSectionIsAbsentAltogether() throws IOException {
        Config config = configOf("other: value\n");
        assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parseInlineBackend(config.getConfigSection("storage")));
    }

    @Test
    void failsWhenMoreThanOneBackendIsDeclared() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  localfile:",
                "    path: \"" + path("a") + "\"",
                "  mongo:",
                "    url: \"mongodb://localhost:27017\"",
                "    db: myplugin",
                ""));
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parseInlineBackend(config.getConfigSection("storage")));
        assertTrue(error.getMessage().contains("EXACTLY ONE"), error.getMessage());
    }

    @Test
    void failsOnAnUnknownType() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  cassandra:",
                "    url: \"whatever\"",
                ""));
        assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parseInlineBackend(config.getConfigSection("storage")));
    }

    // ------------------------------------------------------------------
    // template
    // ------------------------------------------------------------------

    @Test
    void theSeededTemplateParsesToAGroupedfileDefault() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"));

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(storage);
        assertEquals(BackendType.GROUPEDFILE, backend.getType());
        assertEquals(BackendDefinition.FileFormat.YAML, backend.getFormat());
        assertTrue(backend.isEnabled());

        //the block documents the switchable types for the admin
        config.save();
        String raw = new String(Files.readAllBytes(config.getFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("Storage backend"), raw);
        assertTrue(raw.contains("mongo"), "the comment must document the other types: " + raw);
    }

    @Test
    void theTemplateIsIdempotentAndKeepsAnAdminEditedBackend() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  mongo:",
                "    url: \"mongodb://localhost:27017\"",
                "    db: chosen",
                ""));
        ConfigSection storage = config.getConfigSection("storage");

        //an admin already picked mongo: seeding a default must not add a second backend
        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"));

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(storage);
        assertEquals(BackendType.MONGO, backend.getType(), "seeding must not override the admin's choice");
    }

    @Test
    void seedsAChosenFileBackendWithItsRequestedFormat() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.LOCALFILE, BackendDefinition.FileFormat.JSON, false);

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(storage);
        assertEquals(BackendType.LOCALFILE, backend.getType());
        assertEquals(BackendDefinition.FileFormat.JSON, backend.getFormat(), "a file backend honours the requested format");
    }

    @Test
    void aNullFormatOnAFileBackendDefaultsToYaml() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.GROUPEDFILE, null, false);

        assertEquals(BackendDefinition.FileFormat.YAML,
                StorageYamlParser.parseInlineBackend(storage).getFormat());
    }

    @Test
    void seedsANonFileBackendAndDropsTheRequestedFormat() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        //a format was requested, but mongo is not a file backend - it must be overridden away, not seeded
        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.MONGO, BackendDefinition.FileFormat.YAML, false);

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(storage);
        assertEquals(BackendType.MONGO, backend.getType());
        assertFalse(storage.contains("mongo.format"), "format is meaningless on a non-file backend and must not be seeded");
        assertTrue(storage.contains("mongo.url"));
        assertTrue(storage.contains("mongo.db"));
    }

    @Test
    void seedsAnH2BackendWithItsUrl() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.H2, null, false);

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(storage);
        assertEquals(BackendType.H2, backend.getType());
        assertTrue(storage.contains("h2.url"));
        assertFalse(storage.contains("h2.format"));
    }

    @Test
    void seedsAMemoryBackendThatParsesBack() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.MEMORY, null, false);

        assertEquals(BackendType.MEMORY, StorageYamlParser.parseInlineBackend(storage).getType());
    }

    @Test
    void theCompactCommentOmitsTheFullTypeCatalog() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.GROUPEDFILE, null, true);

        config.save();
        String raw = new String(Files.readAllBytes(config.getFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("Declare EXACTLY ONE backend here"), raw);
        assertFalse(raw.contains("Valid types and their fields"),
                "the compact header must not document every type: " + raw);
    }

    // ------------------------------------------------------------------
    // open (end to end through a real file backend)
    // ------------------------------------------------------------------

    @Test
    void openBackendCreatesConnectsAndServesAOwnedRepository() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  localfile:",
                "    path: \"" + path("owned") + "\"",
                "    format: yaml",
                ""));

        Storage storage = ECStorage.openBackend(config.getConfigSection("storage"));
        try {
            EntityDescriptor<UUID, Widget> descriptor = EntityDescriptor.builder(UUID.class, Widget.class)
                    .collection("inline_widgets")
                    .keyExtractor(widget -> widget.id)
                    .codec(new JacksonJsonCodec<>(Widget.class))
                    .build();
            Repository<UUID, Widget> repository = storage.repository(descriptor);

            UUID id = UUID.randomUUID();
            repository.save(new Widget(id, "hello")).join();

            Optional<Widget> found = repository.find(id).join();
            assertTrue(found.isPresent(), "the plugin-owned backend must persist and serve its own data");
            assertEquals("hello", found.get().name);

            //the payload really landed in the plugin's own path, not the core's storage
            assertNotNull(new File(path("owned")).listFiles(), "the owned backend wrote to its own folder");
        } finally {
            storage.close().join();
        }
    }

    public static class Widget {
        public UUID id;
        public String name;

        public Widget() {
        }

        public Widget(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
