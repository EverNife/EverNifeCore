package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyImportReport;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyPlayerDataImporter;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console narrative an import run produces, read off the report the importer itself builds.
 *
 * <p>Lives in the controller's package on purpose: the importer needs the resolved bindings, and the
 * controller hands those out to its own package only. The boot passes the report straight to the
 * logger and drops it, so driving the importer from here is the only way to read one back - and the
 * only way to prove that the run's numbers really reach the report, instead of the progress file
 * alone.</p>
 */
@ECoreTest
class LegacyImportReportTest {

    private static final UUID PETRUS_UUID = UUID.fromString("068117bc-0000-4000-8000-000000000011");


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class LegacyJobsPDSection extends PDSection {
        public int level;
        public String job = "none";
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private File legacyFolder() {
        return tempDir.resolve("PlayerData").toFile();
    }

    private File metadataFile() {
        return tempDir.resolve("playerdata-storage-migration-metadata.yml").toFile();
    }

    private File writeStorageYml() throws IOException {
        String dataPath = tempDir.resolve("storagedata").toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "default-backend: test_files",
                "");
        File file = tempDir.resolve("storage.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void writePetrusYml() throws IOException {
        File folder = legacyFolder();
        folder.mkdirs();
        Files.write(new File(folder, "petrus.yml").toPath(), String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                "").getBytes(StandardCharsets.UTF_8));
    }

    private void registerJobsSection() {
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

    /**
     * Boots with an empty legacy folder (so the boot itself imports nothing), then runs the real
     * importer over the files written afterwards, with the bindings that boot resolved.
     */
    private LegacyImportReport bootThenImport() throws IOException {
        registerJobsSection();
        PlayerController.initialize(writeStorageYml());
        writePetrusYml();
        PlayerController controller = PlayerController.get();
        return new LegacyPlayerDataImporter(legacyFolder(), controller.playerDataBinding(),
                controller.sectionBindings()).run();
    }

    private static File[] ymlFiles(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        return files != null ? files : new File[0];
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    void theReportOfARealRunNarratesEveryRootKeyItFound() throws IOException {
        String text = bootThenImport().format();

        //the run's own numbers reach the console, not just the progress file
        assertTrue(text.contains("2 root key(s)"), text);
        assertTrue(text.contains("FinalJobs"), text);
        assertTrue(text.contains("LegacyJobsPDSection"), text);
        assertTrue(text.contains("[DONE]"), text);
        assertTrue(text.contains("1 file(s) found"), text);
        assertTrue(text.contains("1 file(s) fully imported"), text);
        assertTrue(text.contains("0 file(s) still pending"), text);

        //a section registered without an ECPluginData has no owner to name - and must still print
        assertTrue(text.contains("unknown plugin"), text);
    }

    @Test
    void theRunThatDrainsTheFolderWarnsAboutTheDowngrade() throws IOException {
        LegacyImportReport report = bootThenImport();
        String text = report.format();

        assertTrue(report.isBecameCompleteThisRun(), "this run is the one that emptied the folder");
        assertEquals(0, ymlFiles(legacyFolder()).length);
        assertTrue(text.contains("ROLLBACK"), text);
        assertTrue(text.contains("DOWNGRADE"), text);
        assertTrue(text.contains("__LegacyData_V2"),
                "the completion notice must say where the files were consolidated: " + text);
    }

    @Test
    void aRunThatFindsTheMigrationAlreadyCompleteRepeatsNoWarning() throws IOException {
        registerJobsSection();
        PlayerController.initialize(writeStorageYml());
        //the state a finished migration leaves behind: whoever re-runs it (a restored legacy file after
        //deleting the progress file) was already told about the downgrade by the run that drained the folder
        Files.write(metadataFile().toPath(), "complete: true\n".getBytes(StandardCharsets.UTF_8));
        writePetrusYml();

        PlayerController controller = PlayerController.get();
        LegacyImportReport report = new LegacyPlayerDataImporter(legacyFolder(),
                controller.playerDataBinding(), controller.sectionBindings()).run();
        String text = report.format();

        //the run DID complete: only "was it already complete before" can hold the warning back
        assertTrue(report.isMigrationComplete());
        assertEquals(1, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length, "the import really ran");
        assertFalse(report.isBecameCompleteThisRun());
        assertFalse(text.contains("ROLLBACK"), "the warning belongs to the run that drained the folder: " + text);
        assertFalse(text.contains("MIGRATION COMPLETE"), text);
    }
}
