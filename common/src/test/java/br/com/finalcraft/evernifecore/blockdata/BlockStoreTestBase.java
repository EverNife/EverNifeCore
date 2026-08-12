package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.Storages;
import org.junit.jupiter.api.AfterEach;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opens block stores and closes them again. A store left open keeps a flush thread ticking over a backend
 * the next test already tore down, and its {@code Ref} registration would refuse the next store built on the
 * same handle - both fail somewhere else, in another test.
 */
abstract class BlockStoreTestBase {

    private final List<SVWorldDataManager<?>> openStores = new ArrayList<>();
    private final List<ECStorage> openStorages = new ArrayList<>();
    private final List<Storage> openBackends = new ArrayList<>();

    @AfterEach
    void closeEverythingTheTestOpened() {
        for (SVWorldDataManager<?> store : openStores) {
            store.close();
        }
        openStores.clear();
        for (ECStorage storage : openStorages) {
            storage.close().join();
        }
        openStorages.clear();
        for (Storage backend : openBackends) {
            backend.close().join();
        }
        openBackends.clear();
    }

    /** A plugin-owned storage handle - the production path, where the codec and the Ref graph come from. */
    protected ECStorage openStorage(BackendDefinition definition) {
        ECStorage storage = ECStorage.open(definition).join();
        openStorages.add(storage);
        return storage;
    }

    /** An in-memory backend that counts what it was asked to do and can be told to fail a write. */
    protected ScriptedStorage openScriptedStorage() {
        ScriptedStorage backend = ScriptedStorage.wrapping(Storages.createInMemory());
        backend.init().join();
        openBackends.add(backend);
        return backend;
    }

    /** A store on a plugin's handle, flushing only when a test says so. */
    protected SVWorldDataManager<Marker> storeOn(ECStorage storage, String collection) {
        return register(SVWorldDataManager.targeting(Marker.class)
                .on(storage, collection)
                .autoFlushEvery(Duration.ZERO)
                .build().join());
    }

    /** A store on a bare backend - the test-only path, the one a scripted backend can be handed to. */
    protected SVWorldDataManager<Marker> rawStoreOn(Storage backend, String collection) {
        return register(SVWorldDataManager.targeting(Marker.class)
                .on(backend, collection)
                .autoFlushEvery(Duration.ZERO)
                .build().join());
    }

    /** Hands the store to the teardown - for the ones a test builds itself. */
    protected <O> SVWorldDataManager<O> register(SVWorldDataManager<O> store) {
        openStores.add(store);
        return store;
    }

    /** Waits for something a background thread does, and says what it was waiting for when it never happens. */
    protected static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("timed out waiting for " + what);
    }
}
