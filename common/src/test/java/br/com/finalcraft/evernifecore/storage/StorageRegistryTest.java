package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.HealthStatus;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.Storages;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.modules.memory.InMemoryStorage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRegistryTest {

    @Test
    void registerGetAndDefault() {
        InMemoryStorage memory = Storages.createInMemory();
        StorageRegistry registry = new StorageRegistry("mem");
        registry.register("mem", memory);

        assertSame(memory, registry.get("mem"));
        assertSame(memory, registry.getDefaultBackend());
        assertTrue(registry.tryGet("mem").isPresent());
        assertFalse(registry.tryGet("other").isPresent());
        assertEquals("mem", registry.getDefaultBackendName());
    }

    @Test
    void getUnknownBackendThrowsWithHelpfulMessage() {
        StorageRegistry registry = new StorageRegistry("mem");
        registry.register("mem", Storages.createInMemory());

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> registry.get("mysql"));
        assertTrue(error.getMessage().contains("mysql"));
        assertTrue(error.getMessage().contains("mem")); // lists the available ones
    }

    @Test
    void duplicateRegistrationThrows() {
        StorageRegistry registry = new StorageRegistry("mem");
        registry.register("mem", Storages.createInMemory());
        assertThrows(StorageConfigException.class,
                () -> registry.register("mem", Storages.createInMemory()));
    }

    @Test
    void collectionClaims() {
        StorageRegistry registry = new StorageRegistry("mem");

        assertTrue(registry.claimCollection("mem", "players", "EverNifeCore"));
        assertTrue(registry.claimCollection("mem", "players", "EverNifeCore"));   // re-claim by the owner
        assertFalse(registry.claimCollection("mem", "players", "OtherPlugin"));   // clash
        assertTrue(registry.claimCollection("other_backend", "players", "OtherPlugin")); // another backend is ok

        assertEquals("EverNifeCore", registry.getCollectionOwner("mem", "players"));
        assertNull(registry.getCollectionOwner("mem", "unclaimed"));
    }

    @Test
    void initAllAndCloseAllSucceedOnHealthyBackends() {
        StorageRegistry registry = new StorageRegistry("mem");
        registry.register("mem", Storages.createInMemory());
        registry.register("mem2", Storages.createInMemory());

        registry.initAll().join();
        registry.closeAll().join();
    }

    @Test
    void initAllFailsFastNamingTheBrokenBackend() {
        StorageRegistry registry = new StorageRegistry("mem");
        registry.register("mem", Storages.createInMemory());
        registry.register("broken", new FailingStorage());

        CompletionException error = assertThrows(CompletionException.class,
                () -> registry.initAll().join());
        assertTrue(error.getCause() instanceof StorageConfigException);
        assertTrue(error.getCause().getMessage().contains("broken"));
    }

    /** Minimal Storage stub whose init() always fails. */
    private static class FailingStorage implements Storage {
        private StorageLogConfig logConfig = StorageLogConfig.silent();

        @Override
        public CompletableFuture<Void> init() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("connection refused"));
            return future;
        }

        @Override
        public CompletableFuture<Void> close() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<HealthStatus> health() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StorageLogConfig getStorageLogConfig() {
            return logConfig;
        }

        @Override
        public Storage setStorageLogConfig(StorageLogConfig logConfig) {
            this.logConfig = logConfig;
            return this;
        }
    }
}
