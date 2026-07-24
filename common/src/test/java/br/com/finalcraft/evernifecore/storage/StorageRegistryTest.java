package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.HealthStatus;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.Storages;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.modules.memory.InMemoryStorage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    void initAllReportsEveryBrokenBackend() {
        StorageRegistry registry = new StorageRegistry("mem");
        registry.register("mem", Storages.createInMemory());
        registry.register("broken", new FailingStorage());

        CompletionException error = assertThrows(CompletionException.class,
                () -> registry.initAll().join());
        assertTrue(error.getCause() instanceof StorageConfigException);
        assertTrue(error.getCause().getMessage().contains("broken"));
    }

    @Test
    void initAllAggregatesEveryBrokenBackendInsteadOfOnlyTheFirst() {
        StorageRegistry registry = new StorageRegistry("ok");
        registry.register("ok", Storages.createInMemory());
        registry.register("broken1", new FailingStorage());
        registry.register("broken2", new FailingStorage());

        CompletionException error = assertThrows(CompletionException.class,
                () -> registry.initAll().join());
        assertTrue(error.getCause() instanceof StorageUnavailableException);
        StorageUnavailableException unavailable = (StorageUnavailableException) error.getCause();

        assertEquals(2, unavailable.getFailures().size());
        List<String> brokenNames = unavailable.getFailures().stream()
                .map(StorageInitFailure::getBackendName).collect(Collectors.toList());
        assertTrue(brokenNames.contains("broken1"));
        assertTrue(brokenNames.contains("broken2"));
        assertTrue(unavailable.getMessage().contains("broken1"));
        assertTrue(unavailable.getMessage().contains("broken2"));
    }

    @Test
    void closeAllClosesEveryRegisteredBackendAfterAPartialInitFailure() {
        StorageRegistry registry = new StorageRegistry("ok");
        CountingStorage ok = new CountingStorage(false);
        CountingStorage broken = new CountingStorage(true);
        registry.register("ok", ok);
        registry.register("broken", broken);

        assertThrows(CompletionException.class, () -> registry.initAll().join());

        //mirrors PlayerController's constructor: a construction failure must not leak the backend
        //that DID come up - closeAll() is called on the whole (partially-initialized) registry
        registry.closeAll().join();

        assertEquals(1, ok.closeCount.get());
        assertEquals(1, broken.closeCount.get());
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

    /** Storage stub that counts close() calls; init() fails or succeeds depending on the constructor flag. */
    private static class CountingStorage implements Storage {
        private final boolean failsToInit;
        private StorageLogConfig logConfig = StorageLogConfig.silent();
        final AtomicInteger closeCount = new AtomicInteger();

        CountingStorage(boolean failsToInit) {
            this.failsToInit = failsToInit;
        }

        @Override
        public CompletableFuture<Void> init() {
            if (failsToInit) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("connection refused"));
                return future;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> close() {
            closeCount.incrementAndGet();
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
