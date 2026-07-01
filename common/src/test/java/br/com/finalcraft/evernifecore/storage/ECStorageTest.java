package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storages;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ECStorageTest {

    public static class Shop {
        private UUID id;
        private String name;

        public Shop() {}

        public Shop(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class OtherEntity {
        private UUID id;

        public OtherEntity() {}

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
    }

    private StorageRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StorageRegistry("main_storage");
        registry.register("main_storage", Storages.createInMemory());
        registry.register("economy_storage", Storages.createInMemory());
        registry.initAll().join();
        ECStorage.initialize(registry);
    }

    private static <V> EntityDescriptor<UUID, V> descriptor(Class<V> type, String collection,
                                                            java.util.function.Function<V, UUID> key) {
        return EntityDescriptor.builder(UUID.class, type)
                .collection(collection)
                .keyExtractor(key)
                .codec(new JacksonJsonCodec<>(type))
                .build();
    }

    @Test
    void repositoryOnDefaultAndNamedBackends() {
        EntityDescriptor<UUID, Shop> shops = descriptor(Shop.class, "myplugin_shops", Shop::getId);

        Repository<UUID, Shop> repo = ECStorage.repository(shops);
        UUID id = UUID.randomUUID();
        repo.save(new Shop(id, "Magic Shop")).join();
        assertEquals("Magic Shop", repo.find(id).join().orElseThrow(AssertionError::new).getName());

        // same descriptor again -> same repository, no clash (same owner)
        assertSame(repo, ECStorage.repository(shops));

        // the same collection name on ANOTHER backend is allowed
        ECStorage.repository("economy_storage", shops);
    }

    @Test
    void collectionClashAcrossDifferentEntitiesIsHardError() {
        ECStorage.repository(descriptor(Shop.class, "myplugin_data", Shop::getId));

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> ECStorage.repository(descriptor(OtherEntity.class, "myplugin_data", OtherEntity::getId)));
        assertTrue(error.getMessage().contains("myplugin_data"));
    }

    @Test
    void unknownBackendThrows() {
        assertThrows(StorageConfigException.class,
                () -> ECStorage.backend("nonexistent"));
    }
}
