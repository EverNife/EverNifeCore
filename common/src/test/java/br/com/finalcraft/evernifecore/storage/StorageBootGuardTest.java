package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2x2 matrix (boot/reload x flag true/false) that decides whether
 * {@code IPlatform.shutdown} is called - see decisions D2/D3 of the storage boot guard design.
 */
class StorageBootGuardTest {

    @BeforeAll
    static void installTestPlatform() {
        //force, not ensureInstalled(): this test observes shutdownRequests(), which only the
        //fixture's own no-op platform records - another test class may have left a different
        //IPlatform (e.g. a command-capture harness) registered in this shared JVM
        TestPlatformFixture.forceInstallNoop();
    }

    @BeforeEach
    void clearShutdowns() {
        TestPlatformFixture.clearShutdownRequests();
    }

    @AfterEach
    void restoreDefaultFlag() {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;
    }

    @Test
    void bootWithTheFlagOnStopsTheServerExactlyOnce() {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;

        StorageBootGuard.onStorageUnavailable(oneFailure(), false);

        assertEquals(1, TestPlatformFixture.shutdownRequests().size());
    }

    @Test
    void bootWithTheFlagOffNeverStopsTheServer() {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = false;

        StorageBootGuard.onStorageUnavailable(oneFailure(), false);

        assertTrue(TestPlatformFixture.shutdownRequests().isEmpty());
    }

    @Test
    void reloadNeverStopsTheServerRegardlessOfTheFlag() {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;
        StorageBootGuard.onStorageUnavailable(oneFailure(), true);
        assertTrue(TestPlatformFixture.shutdownRequests().isEmpty(),
                "a failed reload must never stop the server even with the flag on");

        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = false;
        StorageBootGuard.onStorageUnavailable(oneFailure(), true);
        assertTrue(TestPlatformFixture.shutdownRequests().isEmpty());
    }

    private static StorageUnavailableException oneFailure() {
        StorageInitFailure failure = new StorageInitFailure("mysql", BackendType.SQL,
                "jdbc:mysql://host/db", new IllegalStateException("refused"));
        return new StorageUnavailableException("Failed to initialize 1 of 1 storage backend(s)",
                Collections.singletonList(failure), Collections.emptyMap(), null);
    }
}
