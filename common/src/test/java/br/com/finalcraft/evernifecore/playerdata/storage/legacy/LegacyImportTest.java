package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy import acceptance tests: given a folder with YAML files in the v3 layout,
 * the first boot imports the base PlayerData (+ cooldowns) and, through a
 * legacyYaml adapter, the registered section; the files are archived and a re-run never duplicates.
 *
 * <p>The test platform fixture runs {@code runOnFirstTick} inline, so the whole
 * import + load pipeline completes within {@code PlayerController.bootstrap}.</p>
 */
class LegacyImportTest {

    private static final UUID PETRUS_UUID = UUID.fromString("068117bc-0000-4000-8000-000000000001");
    private static final UUID SIMPLE_UUID = UUID.fromString("068117bc-0000-4000-8000-000000000002");

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
    }

    public static class LegacyJobsPDSection extends PDSection {
        public int level;
        public String job = "none";
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private File writeStorageYml(String fileName, String extraBlock) throws IOException {
        String dataPath = tempDir.resolve("storagedata").toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "default-backend: test_files",
                extraBlock,
                "");
        File file = tempDir.resolve(fileName).toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File legacyFolder() {
        return tempDir.resolve("PlayerData").toFile();
    }

    private File writeLegacyYml(String fileName, String content) throws IOException {
        File folder = legacyFolder();
        folder.mkdirs();
        File file = new File(folder, fileName);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String petrusV3Yaml() {
        return String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "Cooldown:",
                "  test_kit:",
                "    identifier: test_kit",
                "    timeStart: " + System.currentTimeMillis(),
                "    timeDuration: 99999999",
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                "OrphanPluginSection:",
                "  some: data",
                "");
    }

    private void registerJobsSectionWithAdapter() {
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, LegacyJobsPDSection.class)
                        .legacyYaml("FinalJobs", section -> {
                            LegacyJobsPDSection jobs = new LegacyJobsPDSection();
                            jobs.level = section.getInt("level");
                            jobs.job = section.getString("job");
                            return jobs;
                        })
                        .build());
    }

    private static File[] ymlFiles(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        return files != null ? files : new File[0];
    }

    // ------------------------------------------------------------------
    // base converter (unit)
    // ------------------------------------------------------------------

    @Test
    void convertBaseParsesTheV3LayoutAndDefaults() throws IOException {
        File file = writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "Cooldown:",
                "  no_identifier_block:",
                "    timeStart: 123456",
                "    timeDuration: 654321",
                ""));

        PlayerData converted = LegacyPlayerDataYamlConverter.convertBase(ConfigFactory.open(file));

        assertEquals(PETRUS_UUID, converted.getUniqueId());
        assertEquals("Petrus", converted.getName());
        assertEquals(1600000000000L, converted.getFirstSeen());
        assertEquals(1700000000000L, converted.getLastSeen());
        assertEquals(1700000000000L, converted.getLastSaved(), "missing lastSaved must default to lastSeen");
        //the legacy Cooldown: block is deliberately NOT imported (the player-cooldown flow
        //was removed from PlayerData); it stays intact in the archived YAML file
    }

    // ------------------------------------------------------------------
    // first boot: base + section via adapter, archived
    // ------------------------------------------------------------------

    @Test
    void firstBootImportsBaseAndAdapterSection() throws IOException {
        writeLegacyYml("petrus.yml", petrusV3Yaml());
        writeLegacyYml("simple.yml", String.join("\n",
                "PlayerData:",
                "  Username: Simple",
                "  UUID: " + SIMPLE_UUID,
                "  firstSeen: 1500000000000",
                "  lastSeen: 1500000000001",
                "  lastSaved: 1500000000002",
                ""));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        //base entity came from the backend (start() loads what the import saved)
        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertNotNull(petrus, "the imported player must be loaded after the first-tick pipeline");
        assertEquals("Petrus", petrus.getName());
        assertEquals(1600000000000L, petrus.getFirstSeen());
        assertEquals(1700000000000L, petrus.getLastSeen());

        assertNotNull(PlayerController.getLoaded(SIMPLE_UUID), "base-only files must import too");

        //section routed through the adapter
        LegacyJobsPDSection jobs = petrus.getPDSection(LegacyJobsPDSection.class).join();
        assertEquals(42, jobs.level);
        assertEquals("miner", jobs.job);

        //the section of a player without that root key gets a default instance
        LegacyJobsPDSection simpleJobs = PlayerController.getLoaded(SIMPLE_UUID)
                .getPDSection(LegacyJobsPDSection.class).join();
        assertEquals(0, simpleJobs.level);

        //archiving: every processed file is moved to -Imported, the source folder is drained
        assertEquals(0, ymlFiles(legacyFolder()).length, "the PlayerData folder must be drained");
        File importedFolder = tempDir.resolve("PlayerData-Imported").toFile();
        assertEquals(2, ymlFiles(importedFolder).length);
        assertFalse(tempDir.resolve("PlayerData-Failed").toFile().exists(), "no failures expected");

        //the unmapped root key stays intact inside the archived file
        Config archived = ConfigFactory.open(new File(importedFolder, "petrus.yml"));
        assertTrue(archived.contains("OrphanPluginSection"), "unmapped sections must stay in the archived file");
        assertTrue(archived.contains("FinalJobs"), "files are moved, never rewritten");

        //logins released (the ready gate has completed): a brand-new login works immediately
        PlayerData fresh = PlayerController.handleLogin(UUID.randomUUID(), "Newcomer").join();
        assertNotNull(fresh);
    }

    // ------------------------------------------------------------------
    // broken files are archived as failures, the rest still imports
    // ------------------------------------------------------------------

    @Test
    void brokenFileGoesToFailedFolderAndOthersStillImport() throws IOException {
        writeLegacyYml("broken.yml", "JustSomeGarbage:\n  no: playerdata\n");
        writeLegacyYml("petrus.yml", petrusV3Yaml());

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertNotNull(PlayerController.getLoaded(PETRUS_UUID), "the healthy file must import");
        assertEquals(1, PlayerController.getLoadedCount());

        assertEquals(0, ymlFiles(legacyFolder()).length);
        assertEquals(1, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);
        File[] failed = ymlFiles(tempDir.resolve("PlayerData-Failed").toFile());
        assertEquals(1, failed.length);
        assertEquals("broken.yml", failed[0].getName());
    }

    // ------------------------------------------------------------------
    // Idempotency: a re-run (migrate-legacy: force) never duplicates or overwrites
    // ------------------------------------------------------------------

    @Test
    void forcedReRunSkipsEntitiesAlreadyOnTheBackend() throws IOException {
        writeLegacyYml("petrus.yml", petrusV3Yaml());
        registerJobsSectionWithAdapter();
        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));
        assertEquals("Petrus", PlayerController.getLoaded(PETRUS_UUID).getName());

        //the same UUID comes back with DIFFERENT data - a skipped import must not apply it
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Imposter",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1",
                "  lastSeen: 2",
                "  lastSaved: 3",
                "FinalJobs:",
                "  level: 99",
                "  job: hacker",
                ""));

        PlayerController.bootstrap(writeStorageYml("storage_force.yml",
                "playerdata:\n  migrate-legacy: force"));

        //the backend data is intact (skip by UUID), nothing duplicated
        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertEquals("Petrus", petrus.getName(), "a forced re-run must never overwrite newer backend data");
        assertEquals(1600000000000L, petrus.getFirstSeen());
        assertEquals(42, petrus.getPDSection(LegacyJobsPDSection.class).join().level);
        assertEquals(1, PlayerController.getLoadedCount());

        //the re-imported file is still archived (with an anti-collision suffix), the folder is drained again
        assertEquals(0, ymlFiles(legacyFolder()).length);
        assertEquals(2, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);
    }

    // ------------------------------------------------------------------
    // Trigger: an already-populated backend without 'force' never imports
    // ------------------------------------------------------------------

    @Test
    void noImportWhenTheBackendIsAlreadyPopulated() throws IOException {
        File storageYml = writeStorageYml("storage.yml", "");
        PlayerController.bootstrap(storageYml);
        PlayerController.handleLogin(UUID.randomUUID(), "Resident").join();
        PlayerController.get().flushAll().join();

        writeLegacyYml("late.yml", String.join("\n",
                "PlayerData:",
                "  Username: TooLate",
                "  UUID: " + SIMPLE_UUID,
                "  firstSeen: 1",
                "  lastSeen: 2",
                ""));

        PlayerController.bootstrap(storageYml); //count() > 0 and no force -> no import

        assertNull(PlayerController.getLoaded(SIMPLE_UUID), "no import may run over a populated backend");
        assertEquals(1, ymlFiles(legacyFolder()).length, "the file must stay in place");
        assertFalse(tempDir.resolve("PlayerData-Imported").toFile().exists());
    }
}
