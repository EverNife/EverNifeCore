package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What {@link PlayerController#getRemoteSignal(Class)} answers per backend. */
@ECoreTest
class RemoteSignalTest {

    @TempDir
    Path tempDir;

    public static class SignalSection extends PDSection {
        public int level;
    }

    private static void register() {
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, SignalSection.class, "signalsection").build());
    }

    /** A storage that is none of the local kinds - what a MariaDB, PostgreSQL or Mongo backend looks like here. */
    private static Storage networkStorage() {
        return (Storage) Proxy.newProxyInstance(Storage.class.getClassLoader(), new Class<?>[]{Storage.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
    }

    @Test
    void aCollectionTheCacheSyncCoversIsLive() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.memory()).boot(tempDir)) {
            register();
            assertEquals(RemoteSignal.LIVE, PlayerController.getRemoteSignal(SignalSection.class),
                    "the memory backend has a change feed, so the cache-sync binds it");
        }
    }

    @Test
    void aFileBackendHasNobodyToHearFrom() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.groupedFile()).boot(tempDir)) {
            register();
            assertEquals(RemoteSignal.NONE, PlayerController.getRemoteSignal(SignalSection.class));
        }
    }

    @Test
    void aSharedBackendWithoutSyncOnlyHearsThroughConflicts() {
        assertEquals(RemoteSignal.CONFLICT_ONLY, RemoteSignal.of(false, networkStorage()));
        assertEquals(RemoteSignal.LIVE, RemoteSignal.of(true, networkStorage()));
    }

    @Test
    void anUnregisteredSectionIsRefused() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.memory()).boot(tempDir)) {
            assertThrows(RuntimeException.class, () -> PlayerController.getRemoteSignal(SignalSection.class));
        }
    }
}
