package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ECoreTest
class PDSectionYamlWriterTest {


    @TempDir
    Path tempDir;

    @Test
    void writesEntryWithCommentsAndIsIdempotent() throws IOException {
        File file = tempDir.resolve("storage.yml").toFile();
        StorageYamlDefaults.writeDefault(file);

        Config config = ConfigFactory.open(file);
        boolean wrote = PDSectionYamlWriter.ensureEntry(config,
                "finaljobs", "FinalJobs", "EverNife", "jobs", "A player's job level and progress",
                "localfile",
                Arrays.asList("localfile", "mysql"),
                Collections.singletonList("localfile"));
        assertTrue(wrote);

        // the entry + comments made it to disk
        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("jobs:"));
        assertTrue(raw.contains("A player's job level and progress"));
        //author first, plugin last: the plugin name is the word an admin scans the file for
        assertTrue(raw.contains("PDSection created by EverNife on the Plugin: FinalJobs"), raw);
        assertTrue(raw.contains("Recommended Backend Types: localfile | mysql"));

        // the generated entry is readable by the parser
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        PDSectionAdminConfig section = parsed.getPDSection("FinalJobs", "jobs")
                .orElseThrow(AssertionError::new);
        assertEquals("localfile", section.getBackendName());

        // second call: the entry already exists, nothing is rewritten
        boolean wroteAgain = PDSectionYamlWriter.ensureEntry(ConfigFactory.open(file),
                "finaljobs", "FinalJobs", "EverNife", "jobs", null,
                "mysql",                       // a different default must NOT overwrite the admin's state
                Collections.emptyList(),
                Collections.singletonList("localfile"));
        assertFalse(wroteAgain);
        assertEquals("localfile", StorageYamlParser.parse(file)
                .getPDSection("FinalJobs", "jobs").orElseThrow(AssertionError::new)
                .getBackendName());
    }

    @Test
    void emptySuggestedListFallsBackToAllBackendIds() throws IOException {
        File file = tempDir.resolve("storage2.yml").toFile();
        StorageYamlDefaults.writeDefault(file);

        PDSectionYamlWriter.ensureEntry(ConfigFactory.open(file),
                "myplugin", "MyPlugin", "SomeAuthor", "mysection", null,
                "localfile",
                Collections.emptyList(),
                Arrays.asList("localfile", "h2", "mysql"));

        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("Recommended Backend Types: localfile | h2 | mysql"));
    }
}
