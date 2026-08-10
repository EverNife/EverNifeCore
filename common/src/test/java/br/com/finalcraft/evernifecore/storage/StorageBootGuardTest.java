package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2x2 matrix (boot/reload x flag true/false) that decides whether
 * {@code IPlatform.shutdown} is called: only a failed boot with the flag on stops the server;
 * a failed reload never does, regardless of the flag.
 */
@ECoreTest
class StorageBootGuardTest {


    @BeforeEach
    void clearShutdowns(TestPlatform platform) {
        platform.reset();
    }

    @AfterEach
    void restoreDefaultFlag() {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;
    }

    @Test
    void bootWithTheFlagOnStopsTheServerExactlyOnce(TestPlatform platform) {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;

        StorageBootGuard.onStorageUnavailable(oneFailure(), false);

        assertEquals(1, platform.getShutdownReasons().size());
    }

    @Test
    void bootWithTheFlagOffNeverStopsTheServer(TestPlatform platform) {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = false;

        StorageBootGuard.onStorageUnavailable(oneFailure(), false);

        assertTrue(platform.getShutdownReasons().isEmpty());
    }

    @Test
    void reloadNeverStopsTheServerRegardlessOfTheFlag(TestPlatform platform) {
        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;
        StorageBootGuard.onStorageUnavailable(oneFailure(), true);
        assertTrue(platform.getShutdownReasons().isEmpty(),
                "a failed reload must never stop the server even with the flag on");

        ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = false;
        StorageBootGuard.onStorageUnavailable(oneFailure(), true);
        assertTrue(platform.getShutdownReasons().isEmpty());
    }

    private static StorageUnavailableException oneFailure() {
        StorageInitFailure failure = new StorageInitFailure("mysql", BackendType.SQL,
                "jdbc:mysql://host/db", new IllegalStateException("refused"));
        return new StorageUnavailableException("Failed to initialize 1 of 1 storage backend(s)",
                Collections.singletonList(failure), Collections.emptyMap(), null);
    }
}
