package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.storage.StorageUnavailableException;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end boot path: {@link PlayerController#initialize(File)} with an unreachable backend must
 * report and, depending on the setting, take the server down with it - AC5 of the storage boot guard.
 * {@link RefReloadSurvivalTest#aFailedReloadKeepsTheLiveInstanceAndDoesNotFireCallbacks()} covers the
 * reload side (AC6): a failed reload must never call {@code IPlatform.shutdown}.
 */
class PlayerControllerStorageBootTest {

    @BeforeAll
    static void installTestPlatform() {
        //force, not ensureInstalled(): this test observes shutdownRequests(), which only the
        //fixture's own no-op platform records - another test class may have left a different
        //IPlatform (e.g. a command-capture harness) registered in this shared JVM
        TestPlatformFixture.forceInstallNoop();
    }

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearShutdowns() {
        TestPlatformFixture.clearShutdownRequests();
    }

    @AfterEach
    void teardown() {
        PlayerController.shutdown();
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;
    }

    @Test
    void bootWithAnUnreachableBackendAndTheFlagOnStopsTheServerOnce() throws IOException {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;
        File broken = writeBrokenStorageYml("flag_on");

        assertThrows(StorageUnavailableException.class, () -> PlayerController.initialize(broken));

        assertEquals(1, TestPlatformFixture.shutdownRequests().size());
        assertNull(PlayerController.get(), "a failed BOOT (no previous instance) must leave no live controller");
    }

    @Test
    void bootWithAnUnreachableBackendAndTheFlagOffNeverStopsTheServerButStillFailsTheBoot() throws IOException {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = false;
        File broken = writeBrokenStorageYml("flag_off");

        assertThrows(StorageUnavailableException.class, () -> PlayerController.initialize(broken));

        assertTrue(TestPlatformFixture.shutdownRequests().isEmpty());
        assertNull(PlayerController.get());
    }

    /** An H2 FILE backend with IFEXISTS on a db that was never created: init() fails deterministically, offline. */
    private File writeBrokenStorageYml(String tag) throws IOException {
        String missingDb = tempDir.resolve("never_created_" + tag).toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:file:" + missingDb + ";IFEXISTS=TRUE\"",
                "default-backend: test_h2",
                "");
        File file = tempDir.resolve("storage_broken_" + tag + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
