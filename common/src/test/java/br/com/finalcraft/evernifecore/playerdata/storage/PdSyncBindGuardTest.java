package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The soft half of the bind guard - the one that fires on the network family alone, with no redis
 * block anywhere. It never fired before, because both call sites fed it a flag that was false out of
 * the box, so the warning written for an account section on a lock-less backend never reached anyone.
 */
@ECoreTest
class PdSyncBindGuardTest {

    @TempDir
    Path tempDir;

    /** A parsed config over one groupedfile backend - versioned entities, no optimistic lock. */
    private ParsedStorageConfig parseFileBackedConfig() {
        File yml = Storages.groupedFile().writeTo(tempDir);
        return StorageYamlParser.parse(yml);
    }

    private Storage openNetworkStorage(ParsedStorageConfig parsed) {
        BackendDefinition backend = parsed.getBackend(parsed.getNetworkBackendName()).orElseThrow(
                () -> new IllegalStateException("the network backend must be declared and enabled"));
        Storage storage = backend.createStorage(StorageLogConfig.defaults());
        storage.init().join();
        return storage;
    }

    /** Account is versioned, which is what makes it a candidate for the lock warning. */
    private EntityDescriptor<UUID, Account> accountDescriptor() {
        return EntityDescriptor
                .builder(UUID.class, Account.class)
                .collection("acs_test_section")
                .keyExtractor(Account::getAccountId)
                .codec(new JacksonJsonCodec<>(Account.class))
                .build();
    }

    @Test
    void aNetworkEntityOnALockLessBackendIsWarnedAbout() {
        ParsedStorageConfig parsed = parseFileBackedConfig();
        Storage storage = openNetworkStorage(parsed);
        try {
            EntityDescriptor<UUID, Account> descriptor = accountDescriptor();
            assertTrue(descriptor.isVersioned(), "the fixture only means anything on a versioned entity");
            assertFalse(storage.enforcesOptimisticLock(), "groupedfile is the lock-less case under test");

            List<String> warnings = new ArrayList<>();
            PdSyncBindGuard.check("AccountSection 'test:section'", descriptor, storage, parsed, true, warnings);

            assertEquals(1, warnings.size(), "exactly the network-family warning, and no redis one");
            String warning = warnings.get(0);
            assertTrue(warning.contains("AccountSection 'test:section'"), warning);
            assertTrue(warning.contains("optimistic lock"), warning);
            //the admin has to be told which key to edit, not just that something is wrong
            assertTrue(warning.contains("network.storage-backend-id"), warning);
        } finally {
            storage.close();
        }
    }

    @Test
    void aPlayerScopedEntityOnTheSameBackendIsNotWarnedAbout() {
        ParsedStorageConfig parsed = parseFileBackedConfig();
        Storage storage = openNetworkStorage(parsed);
        try {
            List<String> warnings = new ArrayList<>();
            //same versioned entity, same lock-less backend: only the network reach makes it a risk,
            //and a row one server owns has no concurrent writer to lose to
            PdSyncBindGuard.check("PDSection 'test:section'", accountDescriptor(), storage, parsed,
                    false, warnings);

            assertTrue(warnings.isEmpty(), "warning unconditionally on the factory default is pure noise: " + warnings);
        } finally {
            storage.close();
        }
    }
}
