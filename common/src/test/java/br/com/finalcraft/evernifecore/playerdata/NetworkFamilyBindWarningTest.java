package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bind guard reached through the CALL SITE, not called directly. Both callers used to hand it a
 * flag that shipped false, so this warning was unreachable in a stock install - the guard itself was
 * always correct, which is exactly why testing the guard alone would not have caught it.
 *
 * <p>It rides on the account section the framework registers itself (the network cooldown row), so
 * nothing here depends on a registration this test made: section registries are static and outlive a
 * single test, which would make the result depend on what ran before it.</p>
 */
@ECoreTest
class NetworkFamilyBindWarningTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    private List<String> bootCapturing(Storages storages) {
        File yml = storages.writeTo(tempDir);
        return Logs.capture(() -> PlayerController.initialize(yml));
    }

    @Test
    void anAccountSectionOnALockLessNetworkBackendIsWarnedAbout() {
        List<String> logged = bootCapturing(Storages.groupedFile());

        assertTrue(logged.stream().anyMatch(line -> line.contains("AccountSection")
                        && line.contains("optimistic lock")),
                "expected the lock-less network backend warning, got: " + logged);
    }

    @Test
    void theWarningNamesTheKeyTheAdminHasToEdit() {
        List<String> logged = bootCapturing(Storages.groupedFile());

        //a warning that only says something is wrong costs the admin an investigation
        assertTrue(logged.stream().anyMatch(line -> line.contains("optimistic lock")
                        && line.contains("network.storage-backend-id")),
                "the warning must point at the key that fixes it, got: " + logged);
    }

    @Test
    void anH2NetworkBackendIsWarnedAboutToo() {
        //h2 does NOT enforce the lock either - it is the one backend that can be shared across
        //instances (over tcp) while still losing a concurrent write, which is the case the guard was
        //written for. Every backend the unit suite can reach is non-enforcing, so the silent
        //direction is pinned by varying the family instead, in PdSyncBindGuardTest
        List<String> logged = bootCapturing(Storages.h2("bind_warn_h2"));

        assertTrue(logged.stream().anyMatch(line -> line.contains("optimistic lock")), logged.toString());
    }
}
