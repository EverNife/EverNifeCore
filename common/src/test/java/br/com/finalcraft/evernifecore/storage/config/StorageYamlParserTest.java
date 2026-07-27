package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.log.StorageLogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ECoreTest
class StorageYamlParserTest {


    @TempDir
    Path tempDir;

    private File yml(String content) throws IOException {
        File file = tempDir.resolve("storage_" + System.nanoTime() + ".yml").toFile();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void generatedDefaultTemplateParsesCleanly() {
        File file = tempDir.resolve("default_storage.yml").toFile();
        StorageYamlDefaults.writeDefault(file);
        assertTrue(file.exists());

        ParsedStorageConfig parsed = StorageYamlParser.parse(file);

        assertEquals(6, parsed.getBackends().size());
        assertEquals("groupedfile", parsed.getDefaultBackendName());

        BackendDefinition groupedfile = parsed.getBackend("groupedfile").orElseThrow(AssertionError::new);
        assertTrue(groupedfile.isEnabled());
        assertEquals(BackendType.GROUPEDFILE, groupedfile.getType());
        assertEquals(BackendDefinition.FileFormat.YAML, groupedfile.getFormat());

        // every other default backend ships disabled
        assertFalse(parsed.getBackend("localfile").orElseThrow(AssertionError::new).isEnabled());
        assertFalse(parsed.getBackend("h2").orElseThrow(AssertionError::new).isEnabled());
        assertFalse(parsed.getBackend("mysql").orElseThrow(AssertionError::new).isEnabled());
        assertFalse(parsed.getBackend("postgresql").orElseThrow(AssertionError::new).isEnabled());
        assertFalse(parsed.getBackend("mongo").orElseThrow(AssertionError::new).isEnabled());

        assertEquals("evernifecore_playerdata", parsed.getPlayerData().getCollection());
        assertEquals(PlayerDataAdminConfig.LoadMode.ALL, parsed.getPlayerData().getLoadMode());
        assertEquals(StorageLogLevel.WARN, parsed.getLoggingLevel());
        assertTrue(parsed.getWarnings().isEmpty(), "default template must parse without warnings: "
                + parsed.getWarnings());

        // the generated file carries the English comment header
        String raw = new String(readAll(file), StandardCharsets.UTF_8);
        assertTrue(raw.contains("storage-backends"));
        assertTrue(raw.contains("FREE UNIQUE ID"));
    }

    @Test
    void writeDefaultDoesNotOverwriteExistingFile() throws IOException {
        File file = yml("storage-backends:\n  custom: { enabled: true, type: memory }\ndefault-backend: custom\n");
        long before = file.length();
        StorageYamlDefaults.writeDefault(file);
        assertEquals(before, file.length());
    }

    @Test
    void supportsMultipleBackendsOfTheSameType() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  mysql_economy:",
                "    enabled: true",
                "    type: sql",
                "    url: \"jdbc:mysql://economy-db:3306/minecraft\"",
                "  mysql_points:",
                "    enabled: true",
                "    type: sql",
                "    url: \"jdbc:mysql://points-db:3306/minecraft\"",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "default-backend: localfile",
                ""));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);

        BackendDefinition economy = parsed.getBackend("mysql_economy").orElseThrow(AssertionError::new);
        BackendDefinition points = parsed.getBackend("mysql_points").orElseThrow(AssertionError::new);
        assertEquals(BackendType.SQL, economy.getType());
        assertEquals(BackendType.SQL, points.getType());
        assertNotEquals(economy.getName(), points.getName());

        StorageRegistry registry = StorageYamlParser.buildRegistry(parsed, StorageLogConfig.silent());
        assertEquals(3, registry.getNames().size());
        assertNotEquals(registry.get("mysql_economy"), registry.get("mysql_points"));
    }

    @Test
    void buildsRegistryWithOnlyEnabledBackends() {
        File file = tempDir.resolve("registry_storage.yml").toFile();
        StorageYamlDefaults.writeDefault(file);
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        StorageRegistry registry = StorageYamlParser.buildRegistry(parsed, StorageLogConfig.silent());

        assertEquals(1, registry.getNames().size());
        assertTrue(registry.getNames().contains("groupedfile"));
        assertEquals("groupedfile", registry.getDefaultBackendName());
        assertThrows(StorageConfigException.class, () -> registry.get("mysql"));
    }

    @Test
    void failsWhenDefaultBackendIsNotDeclared() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "default-backend: nope",
                ""));
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(file));
        assertTrue(error.getMessage().contains("nope"));
    }

    @Test
    void failsWhenDefaultBackendIsDisabled() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: false, type: localfile, path: \"data\" }",
                "default-backend: localfile",
                ""));
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(file));
        assertTrue(error.getMessage().contains("DISABLED"));
    }

    @Test
    void failsOnUnknownBackendType() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  weird: { enabled: true, type: oracle, url: \"x\" }",
                "default-backend: weird",
                ""));
        assertThrows(StorageConfigException.class, () -> StorageYamlParser.parse(file));
    }

    @Test
    void failsOnMissingRequiredFieldPerType() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  mysql: { enabled: true, type: sql }",   // url missing
                "default-backend: mysql",
                ""));
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(file));
        assertTrue(error.getMessage().contains("url"));
    }

    @Test
    void failsOnInvalidPlayerdataCollection() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "default-backend: localfile",
                "playerdata:",
                "  collection: \"1-invalid name\"",
                ""));
        assertThrows(StorageConfigException.class, () -> StorageYamlParser.parse(file));
    }

    @Test
    void failsWhenPlayerdataPointsToDisabledBackend() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "  mysql: { enabled: false, type: sql, url: \"jdbc:mysql://x/db\" }",
                "default-backend: localfile",
                "playerdata:",
                "  storage-backend-id: mysql",
                ""));
        assertThrows(StorageConfigException.class, () -> StorageYamlParser.parse(file));
    }

    @Test
    void warnsOnFormatOutsideLocalfile_andIgnoresIt() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "  mysql: { enabled: false, type: sql, url: \"jdbc:mysql://x/db\", format: yaml }",
                "default-backend: localfile",
                ""));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        assertEquals(1, parsed.getWarnings().size());
        assertTrue(parsed.getWarnings().get(0).contains("format"));
    }

    @Test
    void warnsOnEnabledMemoryBackend() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  mem: { enabled: true, type: memory }",
                "default-backend: mem",
                ""));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        assertEquals(1, parsed.getWarnings().size());
        assertTrue(parsed.getWarnings().get(0).contains("EPHEMERAL"));
    }

    @Test
    void parsesRecentLoadMode() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "default-backend: localfile",
                "playerdata:",
                "  load-mode: RECENT",
                "  recent-days: 15",
                ""));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        assertEquals(PlayerDataAdminConfig.LoadMode.RECENT, parsed.getPlayerData().getLoadMode());
        assertEquals(15, parsed.getPlayerData().getRecentDays());
    }

    @Test
    void parsesPdSectionEntries() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "  mysql: { enabled: false, type: sql, url: \"jdbc:mysql://x/db\" }",
                "default-backend: localfile",
                "pdsections:",
                "  FinalJobs:",
                "    JobsPDSection:",
                "      storage-backend-id: mysql",
                "      collection: finaljobs_jobs",
                "      cache: { policy: TTL, ttlSeconds: 30 }",
                ""));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);

        PDSectionAdminConfig section = parsed.getPDSection("FinalJobs", "JobsPDSection")
                .orElseThrow(AssertionError::new);
        assertEquals("mysql", section.getBackendName());           // raw: validated at registration time
        assertEquals("finaljobs_jobs", section.getCollection());
        assertEquals("TTL", section.getCachePolicyName());
        assertEquals(30, section.getCacheTtlSeconds());

        assertFalse(parsed.getPDSection("FinalJobs", "Other").isPresent());
        assertFalse(parsed.getPDSection("OtherPlugin", "JobsPDSection").isPresent());
    }

    @Test
    void unknownLoggingLevelFallsBackToWarnWithWarning() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "default-backend: localfile",
                "logging:",
                "  level: shout",
                ""));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        assertEquals(StorageLogLevel.WARN, parsed.getLoggingLevel());
        assertEquals(1, parsed.getWarnings().size());
    }

    @Test
    void pdSectionBackendIsNullWhenAbsent() throws IOException {
        File file = yml(String.join("\n",
                "storage-backends:",
                "  localfile: { enabled: true, type: localfile, path: \"data\" }",
                "default-backend: localfile",
                "pdsections:",
                "  FinalJobs:",
                "    JobsPDSection: {}",
                ""));
        PDSectionAdminConfig section = StorageYamlParser.parse(file)
                .getPDSection("FinalJobs", "JobsPDSection").orElseThrow(AssertionError::new);
        assertNull(section.getBackendName());
        assertNull(section.getCollection());
        assertNull(section.getCachePolicyName());
        assertNull(section.getCacheTtlSeconds());
    }

    private static byte[] readAll(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
