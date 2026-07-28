package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end of the GROUPEDFILE backend in EverNifeCore: a player's base PlayerData and a PDSection
 * (both keyed by the player UUID) co-locate in ONE yaml file per player, and survive a reboot.
 */
@ECoreTest
class GroupedFileBackendTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class JobsPDSection extends PDSection {
        public int level;
    }

    private File writeStorageYml(Path dataDir) throws IOException {
        String path = dataDir.toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  grouped:",
                "    enabled: true",
                "    type: groupedfile",
                "    path: \"" + path + "\"",
                "    format: yaml",
                "default-backend: grouped",
                "playerdata:",
                "  storage-backend-id: grouped",
                "  collection: evernifecore_playerdata",
                "");
        File file = tempDir.resolve("storage.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void playerBaseAndSectionCoLocateInOneFilePerKeyAndSurviveAReboot() throws IOException {
        Path dataDir = tempDir.resolve("StorageData");
        File storageYml = writeStorageYml(dataDir);

        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, JobsPDSection.class, "jobspdsection").build());
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Petrus").join();
        JobsPDSection jobs = PlayerController.getPDSection(uuid, JobsPDSection.class).join();
        jobs.level = 42;
        jobs.markDirty();
        PlayerController.get().flushAll().join();

        // key-major layout: exactly one <key>.yml file, holding BOTH the base and the section collections
        File[] keyFiles = dataDir.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(keyFiles, "the grouped file directory must exist");
        assertEquals(1, keyFiles.length, "groupedfile writes one file per player key");
        String content = new String(Files.readAllBytes(keyFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("evernifecore_playerdata"), "the base collection is in the key file");
        assertTrue(content.contains("pd_unknownplugin_jobspdsection"),
                "the section collection co-locates in the SAME key file");

        // reboot: the data comes back from the grouped file
        PlayerController.initialize(storageYml);
        JobsPDSection reloaded = PlayerController.getPDSection(uuid, JobsPDSection.class).join();
        assertEquals(42, reloaded.level, "the section must reload from the grouped file after a reboot");
        assertEquals("Petrus", PlayerController.getLoaded(uuid).getName(), "the base must reload too");
    }
}
