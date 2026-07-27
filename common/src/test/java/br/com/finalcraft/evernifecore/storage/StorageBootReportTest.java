package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ECoreTest
class StorageBootReportTest {


    @TempDir
    Path tempDir;

    // ==================================================================
    //  render(): AC4 - name, type, redacted target, usage, one-line cause per failed backend
    // ==================================================================

    @Test
    void rendersEveryFailedBackendWithNameTypeTargetUsageAndCause() {
        StorageInitFailure mysqlFailure = new StorageInitFailure("mysql", BackendType.SQL,
                "jdbc:mysql://localhost:39306/ecore (user 'root')",
                new ConnectException("Connection refused: getsockopt"));
        StorageInitFailure mongoFailure = new StorageInitFailure("mongo", BackendType.MONGO,
                "mongodb://localhost:39308 (db 'ecore')",
                new IllegalStateException("Timed out after 30000 ms"));

        Map<String, List<String>> usages = new LinkedHashMap<>();
        usages.put("mysql", Arrays.asList("default-backend", "playerdata.storage-backend-id"));
        // 'mongo' is deliberately left out of the map: it must render as "no explicit reference"

        StorageUnavailableException failure = new StorageUnavailableException(
                "Failed to initialize 2 of 2 storage backend(s)",
                Arrays.asList(mysqlFailure, mongoFailure), usages,
                new File("plugins/EverNifeCore/storage.yml"));

        String rendered = String.join("\n", StorageBootReport.render(failure, true, false));

        assertTrue(rendered.contains("backend 'mysql'"));
        assertTrue(rendered.contains("backend 'mongo'"));
        assertTrue(rendered.contains("type: sql"));
        assertTrue(rendered.contains("type: mongo"));
        assertTrue(rendered.contains("jdbc:mysql://localhost:39306/ecore (user 'root')"));
        assertTrue(rendered.contains("mongodb://localhost:39308 (db 'ecore')"));
        assertTrue(rendered.contains("default-backend, playerdata.storage-backend-id"));
        assertTrue(rendered.contains("no explicit reference in storage.yml"));
        assertTrue(rendered.contains("ConnectException: Connection refused"));
        assertTrue(rendered.contains("IllegalStateException: Timed out after 30000 ms"));
        assertTrue(rendered.contains("SERVER STOPPED"));
    }

    @Test
    void reloadingBannerNeverStopsAndSaysThePreviousStorageStaysActive() {
        StorageUnavailableException failure = oneFailure();

        String rendered = String.join("\n", StorageBootReport.render(failure, false, true));

        assertFalse(rendered.contains("SERVER STOPPED"));
        assertTrue(rendered.contains("RELOAD"));
        assertTrue(rendered.contains("still live and serving"));
    }

    @Test
    void notStoppingBannerExplainsTheServerStaysUpWithEverNifeCoreDisabled() {
        StorageUnavailableException failure = oneFailure();

        String rendered = String.join("\n", StorageBootReport.render(failure, false, false));

        assertFalse(rendered.contains("SERVER STOPPED"));
        assertTrue(rendered.contains("STOP_SERVER_IF_STORAGE_IS_UNREACHABLE: false"));
        assertTrue(rendered.contains("DISABLED"));
    }

    private static StorageUnavailableException oneFailure() {
        StorageInitFailure failure = new StorageInitFailure("mysql", BackendType.SQL,
                "jdbc:mysql://host/db", new IllegalStateException("refused"));
        return new StorageUnavailableException("Failed to initialize 1 of 1 storage backend(s)",
                Collections.singletonList(failure), Collections.emptyMap(), null);
    }

    // ==================================================================
    //  usagesByBackend()/enrich(): the usages index is derived from a real parsed storage.yml
    // ==================================================================

    @Test
    void enrichAttachesUsagesFromTheParsedStorageYml() throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  mysql:",
                "    enabled: true",
                "    type: sql",
                "    url: \"jdbc:mysql://localhost:39306/ecore\"",
                "    user: root",
                "  mongo:",
                "    enabled: true",
                "    type: mongo",
                "    url: \"mongodb://localhost:39308\"",
                "    db: ecore",
                "default-backend: mysql",
                "playerdata:",
                "  storage-backend-id: mysql",
                "pdsections:",
                "  FinalGuilds:",
                "    GuildSection:",
                "      storage-backend-id: mongo",
                "");
        File file = tempDir.resolve("storage.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        ParsedStorageConfig config = StorageYamlParser.parse(file);

        StorageInitFailure mysqlFailure = new StorageInitFailure("mysql", BackendType.SQL,
                "jdbc:mysql://localhost:39306/ecore (user 'root')", new IllegalStateException("refused"));
        StorageInitFailure mongoFailure = new StorageInitFailure("mongo", BackendType.MONGO,
                "mongodb://localhost:39308 (db 'ecore')", new IllegalStateException("timeout"));
        StorageUnavailableException raw = new StorageUnavailableException(
                "Failed to initialize 2 of 2 storage backend(s)",
                Arrays.asList(mysqlFailure, mongoFailure), Collections.emptyMap(), null);

        StorageUnavailableException enriched = StorageBootReport.enrich(raw, config, file);

        assertEquals(file, enriched.getStorageYmlFile());
        assertTrue(enriched.getUsages().get("mysql").contains("default-backend"));
        assertTrue(enriched.getUsages().get("mysql").contains("playerdata.storage-backend-id"));
        assertTrue(enriched.getUsages().get("mongo").contains("pdsections.FinalGuilds.GuildSection"));
    }

    @Test
    void anEnabledBackendWithNoExplicitReferenceHasNoUsageEntry() throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  main:",
                "    enabled: true",
                "    type: memory",
                "  unused:",
                "    enabled: true",
                "    type: memory",
                "default-backend: main",
                "");
        File file = tempDir.resolve("storage.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        ParsedStorageConfig config = StorageYamlParser.parse(file);

        Map<String, List<String>> usages = StorageBootReport.usagesByBackend(config);

        assertTrue(usages.containsKey("main"));
        assertFalse(usages.containsKey("unused"), "an enabled backend nobody references must have no usage entry");
    }
}
