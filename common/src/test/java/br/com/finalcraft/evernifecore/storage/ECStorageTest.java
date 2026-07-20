package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin-owned storage handle: an opened {@link BackendDefinition} with its own registry and memoized
 * managers. These exercise the reload contract (reuse vs reconnect) and the value identity that drives it,
 * all on the offline memory / groupedfile backends (no Docker, no external services).
 */
class ECStorageTest {

    public static class Shop {
        private UUID id;
        private String name;

        public Shop() {
        }

        public Shop(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static EntityDescriptor<UUID, Shop> descriptor() {
        return EntityDescriptor.builder(UUID.class, Shop.class)
                .collection("ecstorage_shops")
                .keyExtractor(Shop::getId)
                .codec(new JacksonJsonCodec<>(Shop.class))
                .build();
    }

    @Test
    void backendDefinitionEqualityIsValueBased() {
        assertEquals(BackendDefinition.memory(), BackendDefinition.memory());
        assertEquals(BackendDefinition.groupedFile("data/x", BackendDefinition.FileFormat.JSON),
                BackendDefinition.groupedFile("data/x", BackendDefinition.FileFormat.JSON));
        assertEquals(BackendDefinition.groupedFile("data/x", BackendDefinition.FileFormat.JSON).hashCode(),
                BackendDefinition.groupedFile("data/x", BackendDefinition.FileFormat.JSON).hashCode());

        assertNotEquals(BackendDefinition.memory(), BackendDefinition.groupedFile("data/x", null));
        assertNotEquals(BackendDefinition.groupedFile("data/x", BackendDefinition.FileFormat.JSON),
                BackendDefinition.groupedFile("data/x", BackendDefinition.FileFormat.YAML));
        assertNotEquals(BackendDefinition.groupedFile("data/a", null),
                BackendDefinition.groupedFile("data/b", null));
    }

    @Test
    void managerIsMemoizedPerEntityType() {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        try {
            CachingManager<UUID, Shop> first = storage.manager(descriptor(), CachePolicy.always());
            CachingManager<UUID, Shop> second = storage.manager(descriptor(), CachePolicy.always());
            assertSame(first, second, "a second manager() for the same type returns the memoized one");
        } finally {
            storage.close().join();
        }
    }

    @Test
    void openOrReloadReusesTheHandleOnAnEqualDefinition() {
        ECStorage first = ECStorage.open(BackendDefinition.memory()).join();
        CachingManager<UUID, Shop> managerBefore = first.manager(descriptor(), CachePolicy.always());

        ECStorage reloaded = ECStorage.openOrReload(BackendDefinition.memory(), first).join();
        assertSame(first, reloaded, "an equal definition reuses the same live handle (no reconnect)");
        assertTrue(reloaded.isOpen());

        CachingManager<UUID, Shop> managerAfter = reloaded.manager(descriptor(), CachePolicy.always());
        assertNotSame(managerBefore, managerAfter, "reset() drops the old managers; a fresh one is built");
        reloaded.close().join();
    }

    @Test
    void openOrReloadReconnectsAndClosesTheOldHandleOnADifferentDefinition(@TempDir Path tempDir) {
        ECStorage first = ECStorage.open(BackendDefinition.memory()).join();
        ECStorage reloaded = ECStorage.openOrReload(
                BackendDefinition.groupedFile(tempDir.toString(), BackendDefinition.FileFormat.YAML), first).join();

        assertNotSame(first, reloaded, "a different definition opens a fresh handle");
        assertFalse(first.isOpen(), "the old handle is closed on a reconnect");
        assertTrue(reloaded.isOpen());
        reloaded.close().join();
    }

    @Test
    void flushManagersCompletesWhenNothingIsDirty() {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        try {
            storage.manager(descriptor(), CachePolicy.always());
            storage.flushManagers().join(); // no dirty cells - a clean, completed flush
        } finally {
            storage.close().join();
        }
    }

    @Test
    void aClosedHandleRejectsFurtherUse() {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        storage.close().join();
        assertFalse(storage.isOpen());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> storage.manager(descriptor(), CachePolicy.always()));
    }
}
