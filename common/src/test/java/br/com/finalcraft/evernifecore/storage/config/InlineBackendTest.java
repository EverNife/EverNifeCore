package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
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
import java.util.concurrent.CompletionException;

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
    void openCreatesConnectsAndServesAnOwnedRepository() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  localfile:",
                "    path: \"" + path("owned") + "\"",
                "    format: yaml",
                ""));

        ECStorage backend = ECStorage.open(config.getConfigSection("storage")).join();
        try {
            EntityDescriptor<UUID, Widget> descriptor = EntityDescriptor.builder(UUID.class, Widget.class)
                    .collection("inline_widgets")
                    .keyExtractor(widget -> widget.id)
                    .codec(new JacksonJsonCodec<>(Widget.class))
                    .build();
            Repository<UUID, Widget> repository = backend.storage().repository(descriptor);

            UUID id = UUID.randomUUID();
            repository.save(new Widget(id, "hello")).join();

            Optional<Widget> found = repository.find(id).join();
            assertTrue(found.isPresent(), "the plugin-owned backend must persist and serve its own data");
            assertEquals("hello", found.get().name);

            //the handle carries the definition it was opened from (a yaml file backend)
            assertEquals(BackendType.LOCALFILE, backend.definition().getType());
            assertEquals(BackendDefinition.FileFormat.YAML, backend.definition().getFormat());

            //the payload really landed in the plugin's own path, not the core's storage
            assertNotNull(new File(path("owned")).listFiles(), "the owned backend wrote to its own folder");
        } finally {
            backend.close().join();
        }
    }

    @Test
    void openSurfacesAnInitFailureAsAStorageConfigException() throws IOException {
        // an H2 file backend that refuses to auto-create (IFEXISTS on a database that does not exist)
        // fails at init() deterministically and offline. The returned future completes exceptionally with a
        // StorageConfigException (join() wraps it in a CompletionException) - never a bare CompletionException
        // carrying the raw driver error.
        String missingDb = tempDir.resolve("never_created_db").toString().replace("\\", "/");
        Config config = configOf(String.join("\n",
                "storage:",
                "  h2:",
                "    url: \"jdbc:h2:file:" + missingDb + ";IFEXISTS=TRUE\"",
                ""));

        CompletionException wrapper = assertThrows(CompletionException.class,
                () -> ECStorage.open(config.getConfigSection("storage")).join(),
                "a failed open must complete the future exceptionally");
        assertTrue(wrapper.getCause() instanceof StorageConfigException,
                "the init failure must surface as a StorageConfigException, got: " + wrapper.getCause());
    }

    // ------------------------------------------------------------------
    // format parsing (alias + validation)
    // ------------------------------------------------------------------

    @Test
    void acceptsYmlAsAnAliasOfYaml() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  groupedfile:",
                "    path: \"" + path("aliased") + "\"",
                "    format: yml",
                ""));

        BackendDefinition backend = StorageYamlParser.parseInlineBackend(config.getConfigSection("storage"));
        assertEquals(BackendDefinition.FileFormat.YAML, backend.getFormat(),
                "'yml' must be read as the yaml format, not rejected");
    }

    @Test
    void rejectsAnUnknownFormatValue() throws IOException {
        Config config = configOf(String.join("\n",
                "storage:",
                "  groupedfile:",
                "    path: \"" + path("bad") + "\"",
                "    format: xml",
                ""));

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parseInlineBackend(config.getConfigSection("storage")));
        assertTrue(error.getMessage().contains("invalid format"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // default codec (the config's type/format decides the wire codec)
    // ------------------------------------------------------------------

    @Test
    void defaultCodecFollowsBackendTypeAndFormat() throws IOException {
        BackendDefinition yamlFile = parseStorage(String.join("\n",
                "storage:",
                "  groupedfile:",
                "    path: \"" + path("yamlfmt") + "\"",
                "    format: yaml",
                ""));
        assertEquals("application/yaml", yamlFile.defaultCodec(Widget.class).contentType(),
                "a yaml file backend serializes as yaml");

        BackendDefinition jsonFile = parseStorage(String.join("\n",
                "storage:",
                "  groupedfile:",
                "    path: \"" + path("jsonfmt") + "\"",
                "    format: json",
                ""));
        assertEquals("application/json", jsonFile.defaultCodec(Widget.class).contentType());
        assertTrue(encodedText(jsonFile).contains("\n"),
                "json on a file backend is pretty/indented (a human may open the file)");

        BackendDefinition nonFile = parseStorage(String.join("\n",
                "storage:",
                "  mongo:",
                "    url: \"mongodb://localhost:27017\"",
                "    db: myplugin",
                ""));
        assertEquals("application/json", nonFile.defaultCodec(Widget.class).contentType());
        assertFalse(encodedText(nonFile).contains("\n"),
                "a non-file backend uses compact json (the payload is parsed, not read by a human)");
    }

    @Test
    void seededGroupedfileJsonWritesJsonFormatToTheFile() throws IOException {
        Config config = configOf("triggers:\n  on-death: true\n");
        ConfigSection storage = config.getConfigSection("storage");

        // a plugin standardizing its factory default on groupedfile + json (not the yaml convenience default)
        StorageYamlDefaults.writeInlineBackendTemplate(storage, path("SnapshotData"),
                BackendType.GROUPEDFILE, BackendDefinition.FileFormat.JSON, false);

        config.save();
        String raw = new String(Files.readAllBytes(config.getFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("format: json"),
                "the seeded groupedfile default must write format: json to the file: " + raw);
    }

    @Test
    void openWritesTheContainerFormatTheConfigChose() throws IOException {
        // the whole point of the fix: the on-disk container extension follows the configured format,
        // end to end through open + the config-driven default codec.
        assertContainerExtension("json", ".json");
        assertContainerExtension("yaml", ".yml");
    }

    private void assertContainerExtension(String format, String expectedExtension) throws IOException {
        String dir = "container_" + format;
        Config config = configOf(String.join("\n",
                "storage:",
                "  groupedfile:",
                "    path: \"" + path(dir) + "\"",
                "    format: " + format,
                ""));

        ECStorage backend = ECStorage.open(config.getConfigSection("storage")).join();
        try {
            EntityDescriptor<UUID, Widget> descriptor = EntityDescriptor.builder(UUID.class, Widget.class)
                    .collection("container_widgets")
                    .keyExtractor(widget -> widget.id)
                    .codec(backend.defaultCodec(Widget.class))   // the codec the config chose
                    .build();
            backend.storage().repository(descriptor).save(new Widget(UUID.randomUUID(), "hi")).join();

            File[] files = new File(path(dir)).listFiles();
            assertNotNull(files, "the backend wrote to its own folder");
            boolean hasExpected = false;
            StringBuilder seen = new StringBuilder();
            for (File file : files) {
                seen.append(file.getName()).append(' ');
                if (file.getName().endsWith(expectedExtension)) {
                    hasExpected = true;
                }
            }
            assertTrue(hasExpected, "a " + format + " backend must write a " + expectedExtension
                    + " container; found: " + seen);
        } finally {
            backend.close().join();
        }
    }

    private BackendDefinition parseStorage(String yaml) throws IOException {
        return StorageYamlParser.parseInlineBackend(configOf(yaml).getConfigSection("storage"));
    }

    private static String encodedText(BackendDefinition backend) {
        return new String(backend.defaultCodec(Widget.class).encode(new Widget(UUID.randomUUID(), "x")),
                StandardCharsets.UTF_8);
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
