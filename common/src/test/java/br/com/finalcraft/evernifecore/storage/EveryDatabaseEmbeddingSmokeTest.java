package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storages;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.modules.memory.InMemoryStorage;
import br.com.finalcraft.everydatabase.query.Indexed;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.versioned.OptimisticLock;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Embedding smoke test: proves that everydatabase-core is on common's compile/test
 * classpath and that EntityDescriptor.build()'s annotation scan (@Indexed /
 * @OptimisticLock) works in a real round-trip (InMemory, no Docker).
 */
class EveryDatabaseEmbeddingSmokeTest {

    public static class SmokeEntity {

        private UUID uuid;

        @Indexed
        private String name;

        @Indexed
        private long lastSeen;

        @OptimisticLock
        private Long lockVersion;

        public SmokeEntity() {}

        public SmokeEntity(UUID uuid, String name, long lastSeen) {
            this.uuid = uuid;
            this.name = name;
            this.lastSeen = lastSeen;
        }

        public UUID getUuid() { return uuid; }
        public void setUuid(UUID uuid) { this.uuid = uuid; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

        public Long getLockVersion() { return lockVersion; }
        public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
    }

    @Test
    void inMemoryRoundTrip_withAnnotatedIndexesAndOptimisticLock() {
        EntityDescriptor<UUID, SmokeEntity> descriptor = EntityDescriptor
                .builder(UUID.class, SmokeEntity.class)
                .collection("smoke_players")
                .keyExtractor(SmokeEntity::getUuid)
                .codec(new JacksonJsonCodec<>(SmokeEntity.class))
                .build();

        InMemoryStorage storage = Storages.createInMemory();
        storage.init().join();
        try {
            Repository<UUID, SmokeEntity> repo = storage.repository(descriptor);

            UUID id = UUID.randomUUID();
            SmokeEntity entity = new SmokeEntity(id, "EverNife", 1000L);
            repo.save(entity).join();

            Optional<SmokeEntity> found = repo.find(id).join();
            assertTrue(found.isPresent());
            assertEquals("EverNife", found.get().getName());
            assertEquals(1000L, found.get().getLastSeen());

            // annotated index (@Indexed name) responding to findBy
            assertEquals(1, repo.findBy("name", "EverNife").join().size());

            // range query on the long index (@Indexed lastSeen) - basis of the RECENT load-mode
            assertEquals(1, repo.query(Query.range("lastSeen", 500L, 2000L)).join().size());
            assertEquals(0, repo.query(Query.range("lastSeen", 2001L, null)).join().size());

            // @OptimisticLock detected by build()'s annotation scan
            // (InMemory does not enforce the version - pure upsert; real enforcement
            // is covered by the EveryDatabase project's MySQL/PG/Mongo suite)
            assertTrue(descriptor.isVersioned());

            // second save (upsert) works normally
            entity.setLastSeen(1500L);
            repo.save(entity).join();
            assertEquals(1500L, repo.find(id).join().get().getLastSeen());
        } finally {
            storage.close().join();
        }
    }
}
