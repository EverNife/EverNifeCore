package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.everydatabase.transfer.TransferReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime transfer acceptance tests: transfer of a PDSection
 * (InMemory -> H2) and of the base PlayerData (LocalFile yaml -> H2 json) with verified
 * counts; a failed transfer never changes the binding.
 */
@ECoreTest
class StorageTransferRuntimeTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class TransferJobsPDSection extends PDSection {
        public int level;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private File writeStorageYml(String fileName, String defaultBackend, String h2Db) throws IOException {
        String dataPath = tempDir.resolve("storagedata").toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "  test_mem:",
                "    enabled: true",
                "    type: memory",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + h2Db + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: " + defaultBackend,
                "");
        File file = tempDir.resolve(fileName).toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // PDSection: InMemory -> H2 (cutover + admin choice persisted)
    // ------------------------------------------------------------------

    @Test
    void transferPDSection_memoryToH2_cutsOverAndPersistsTheChoice() throws IOException {
        File storageYml = writeStorageYml("storage.yml", "test_files", "transfer_section");
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, TransferJobsPDSection.class, "transferjobs")
                        .defaultBackend("test_mem")
                        .build());
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Petrus").join();
        TransferJobsPDSection section = PlayerController.getPDSection(uuid, TransferJobsPDSection.class).join();
        section.level = 42;
        section.markDirty();
        PlayerController.get().flushAll().join();

        TransferReport report = PlayerController.get()
                .transferPDSection(TransferJobsPDSection.class, "test_h2").join();
        assertTrue(report.success(), "transfer must succeed: " + report.errors());
        assertEquals(1, report.totalEntities());

        //the admin choice was persisted in storage.yml (survives reboots)
        String persistedBackend = ConfigFactory.open(storageYml)
                .getString("pdsections.unknownplugin.transferjobs.storage-backend-id");
        assertEquals("test_h2", persistedBackend, "the transfer must persist the new backend in storage.yml");

        //post-transfer writes go to the NEW backend (the in-memory one dies on reboot)
        section.level = 99;
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.initialize(storageYml);
        TransferJobsPDSection reloaded = PlayerController.getPDSection(uuid, TransferJobsPDSection.class).join();
        assertEquals(99, reloaded.level, "the post-transfer write must come back from H2 after a reboot");
    }

    // ------------------------------------------------------------------
    // PlayerData: LocalFile(yaml) -> H2(json) with verified counts
    // ------------------------------------------------------------------

    @Test
    void transferPlayerData_localFileYamlToH2Json_withVerifiedCounts() throws IOException {
        File storageYml = writeStorageYml("storage.yml", "test_files", "transfer_base");
        PlayerController.initialize(storageYml);

        UUID first = UUID.randomUUID();
        PlayerController.handleLogin(first, "First").join();
        PlayerController.handleLogin(UUID.randomUUID(), "Second").join();
        PlayerController.handleLogin(UUID.randomUUID(), "Third").join();
        PlayerController.get().flushAll().join();

        TransferReport report = PlayerController.get().transferPlayerData("test_h2").join();
        assertTrue(report.success(), "transfer must succeed: " + report.errors());
        assertEquals(3, report.totalEntities(), "verifyCounts: all three players copied");

        String persistedBackend = ConfigFactory.open(storageYml)
                .getString("playerdata.storage-backend-id");
        assertEquals("test_h2", persistedBackend, "playerdata.storage-backend-id must be persisted in storage.yml");

        //reboot: the players come back from H2 (the persisted choice takes precedence over default-backend)
        PlayerController.initialize(storageYml);
        assertEquals(3, PlayerController.getLoadedCount());
        assertEquals("First", PlayerController.getLoaded(first).getName());
    }

    // ------------------------------------------------------------------
    // Failure: non-empty target -> reports failure, binding intact
    // ------------------------------------------------------------------

    @Test
    void failedTransferKeepsTheBindingIntact() throws IOException {
        File storageYml = writeStorageYml("storage.yml", "test_files", "transfer_fail");
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, TransferJobsPDSection.class, "transferjobs")
                        .defaultBackend("test_mem")
                        .build());
        PlayerController.initialize(storageYml);

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Petrus").join();
        TransferJobsPDSection section = PlayerController.getPDSection(uuid, TransferJobsPDSection.class).join();
        section.level = 42;
        section.markDirty();
        PlayerController.get().flushAll().join();

        //move to H2 (the source in test_mem is kept as a backup)...
        assertTrue(PlayerController.get().transferPDSection(TransferJobsPDSection.class, "test_h2").join().success());

        //...then moving BACK fails: the target (test_mem) still holds the backup data
        TransferReport backwards = PlayerController.get()
                .transferPDSection(TransferJobsPDSection.class, "test_mem").join();
        assertFalse(backwards.success(), "failIfTargetCollectionNotEmpty must abort the transfer");

        //test_mem already held this collection's claim (it was the original backend, kept as a backup),
        //so the failed transfer back must NOT release it - only a claim the transfer freshly created is
        //released on failure. Over-releasing here would drop the legitimate backup claim.
        String collection = BindingResolver.defaultCollection("UnknownPlugin", "transferjobs");
        assertNotNull(PlayerController.get().registry().getCollectionOwner("test_mem", collection),
                "a failed transfer must not release the pre-existing backup claim on the target");

        //the binding is intact: writes keep going to H2 and survive a reboot
        section.level = 77;
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.initialize(storageYml);
        assertEquals(77, PlayerController.getPDSection(uuid, TransferJobsPDSection.class).join().level,
                "after a failed transfer the section must still live on the previous backend (H2)");
    }

    // ------------------------------------------------------------------
    // Validation: unknown target / same backend -> failed future
    // ------------------------------------------------------------------

    @Test
    void invalidTransferTargetsFailFast() throws IOException {
        File storageYml = writeStorageYml("storage.yml", "test_files", "transfer_invalid");
        PlayerController.initialize(storageYml);
        PlayerController.handleLogin(UUID.randomUUID(), "Someone").join();

        CompletionException unknown = assertThrows(CompletionException.class,
                () -> PlayerController.get().transferPlayerData("nope_backend").join());
        assertNotNull(unknown.getCause());
        assertTrue(unknown.getCause().getMessage().contains("not declared"));

        CompletionException same = assertThrows(CompletionException.class,
                () -> PlayerController.get().transferPlayerData("test_files").join());
        assertTrue(same.getCause().getMessage().contains("Already stored"));
    }
}
