package br.com.finalcraft.evernifecore.worlddata;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.worlddata.manager.SVDataManager;
import br.com.finalcraft.evernifecore.worlddata.manager.storage.WorldChunkData;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.Storages;
import br.com.finalcraft.everydatabase.modules.sql.SqlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance suite for the EveryDatabase-backed {@code SVDataManager}: a Jackson block value
 * round-trips through a per-chunk entity on a real backend (InMemory + H2), {@code load()} rebuilds
 * the in-memory {@code worldDataMap}, the chunk String key encodes/decodes, and the one-time legacy
 * region-YAML importer produces identical block->value associations.
 */
class SVDataManagerTest {

    private static final String WORLD = "world";

    private Storage storage;

    @AfterEach
    void teardown() {
        if (storage != null) {
            storage.close().join();
            storage = null;
        }
    }

    /** A minimal Jackson-serializable block value POJO. */
    public static class Marker {
        public String owner;
        public int amount;

        public Marker() {
        }

        public Marker(String owner, int amount) {
            this.owner = owner;
            this.amount = amount;
        }
    }

    private SVDataManager<Marker> newManager(Storage backend, String collection) {
        return SVDataManager.targeting(Marker.class)
                .on(backend, collection)
                .build();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Chunk-key round-trip
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void chunkKeyEncodeDecodeRoundTrips() {
        ChunkPos chunkPos = ChunkPos.of(3, -7);
        String key = WorldChunkData.keyOf("my_world", chunkPos);

        assertEquals("my_world/3/-7", key);
        assertEquals("my_world", WorldChunkData.worldOf(key));
        assertEquals(chunkPos, WorldChunkData.chunkPosOf(key));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  set / get / remove round-trip through the backend (InMemory)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void setGetRemoveRoundTripsThroughChunkEntity() {
        storage = Storages.createInMemory();
        storage.init().join();

        SVDataManager<Marker> manager = newManager(storage, "svdata_markers");

        BlockPos blockA = BlockPos.of(5, 64, 7);     // chunk (0,0)
        BlockPos blockB = BlockPos.of(20, 70, 3);    // chunk (1,0)

        manager.setBlockData(WORLD, blockA, new Marker("alice", 10));
        manager.setBlockData(WORLD, blockB, new Marker("bob", 20));
        manager.save();

        //a fresh manager on the same backend reloads it
        SVDataManager<Marker> reloaded = newManager(storage, "svdata_markers");
        int loaded = reloaded.load();
        assertEquals(2, loaded);

        Marker a = reloaded.getBlockData(WORLD, blockA);
        assertNotNull(a);
        assertEquals("alice", a.owner);
        assertEquals(10, a.amount);

        Marker b = reloaded.getBlockData(WORLD, blockB);
        assertNotNull(b);
        assertEquals("bob", b.owner);

        //remove one block, persist, reload: the chunk (1,0) becomes empty -> its entity is deleted
        reloaded.setBlockData(WORLD, blockB, null);
        reloaded.save();

        SVDataManager<Marker> afterRemove = newManager(storage, "svdata_markers");
        assertEquals(1, afterRemove.load());
        assertNotNull(afterRemove.getBlockData(WORLD, blockA));
        assertNull(afterRemove.getBlockData(WORLD, blockB));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A real DB backend (H2) round-trip - no region YAML files
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void roundTripThroughH2Backend() {
        String dbName = "svdata_" + UUID.randomUUID().toString().replace("-", "");
        storage = Storages.createH2(new SqlConfig("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "", ""));
        storage.init().join();

        SVDataManager<Marker> manager = newManager(storage, "svdata_h2");
        BlockPos block = BlockPos.of(100, 65, -50);
        manager.setBlockData(WORLD, block, new Marker("carol", 99));
        manager.save();

        SVDataManager<Marker> reloaded = newManager(storage, "svdata_h2");
        assertEquals(1, reloaded.load());
        Marker value = reloaded.getBlockData(WORLD, block);
        assertNotNull(value);
        assertEquals("carol", value.owner);
        assertEquals(99, value.amount);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  preloadAll populates the in-memory worldDataMap
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void loadPopulatesTheWorldDataMap() {
        storage = Storages.createInMemory();
        storage.init().join();

        SVDataManager<Marker> writer = newManager(storage, "svdata_preload");
        writer.setBlockData(WORLD, BlockPos.of(1, 64, 1), new Marker("a", 1));
        writer.setBlockData("nether", BlockPos.of(2, 64, 2), new Marker("b", 2));
        writer.save();

        SVDataManager<Marker> reader = newManager(storage, "svdata_preload");
        assertTrue(reader.getWorldDataMap().isEmpty(), "the map must be empty before load()");
        reader.load();

        assertEquals(2, reader.getWorldDataMap().size(), "both worlds must be mirrored in memory");
        assertNotNull(reader.getWorldData(WORLD));
        assertNotNull(reader.getWorldData("nether"));
        assertEquals(2, reader.getAllBlockMetaData().size());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Legacy importer: r.0.0.yml fixture -> identical block->value associations in the backend
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void legacyImporterConvertsRegionYamlIntoIdenticalAssociations() throws Exception {
        storage = Storages.createInMemory();
        storage.init().join();

        //hand-written legacy region file: <folder>/<world>/r.0.0.yml with chunkPos.blockPos -> Marker
        //chunkPos "0|0" holds two blocks; chunkPos "1|0" holds one
        File legacyFolder = Files.createTempDirectory("svdata-legacy").toFile();
        File worldFolder = new File(legacyFolder, WORLD);
        worldFolder.mkdirs();
        String regionYaml = String.join("\n",
                "'0|0':",
                "  '5|64|7':",
                "    owner: alice",
                "    amount: 10",
                "  '8|64|9':",
                "    owner: dave",
                "    amount: 30",
                "'1|0':",
                "  '20|70|3':",
                "    owner: bob",
                "    amount: 20",
                "");
        Files.write(new File(worldFolder, "r.0.0.yml").toPath(), regionYaml.getBytes(StandardCharsets.UTF_8));

        SVDataManager<Marker> manager = SVDataManager.targeting(Marker.class)
                .on(storage, "svdata_legacy")
                .importingLegacyFrom(legacyFolder)
                .build();

        int imported = manager.importLegacy();
        assertEquals(3, imported, "every legacy block value must be imported");

        //load from the backend and assert the exact same block->value associations
        int loaded = manager.load();
        assertEquals(3, loaded);

        Marker alice = manager.getBlockData(WORLD, BlockPos.of(5, 64, 7));
        assertNotNull(alice);
        assertEquals("alice", alice.owner);
        assertEquals(10, alice.amount);

        Marker dave = manager.getBlockData(WORLD, BlockPos.of(8, 64, 9));
        assertNotNull(dave);
        assertEquals("dave", dave.owner);
        assertEquals(30, dave.amount);

        Marker bob = manager.getBlockData(WORLD, BlockPos.of(20, 70, 3));
        assertNotNull(bob);
        assertEquals("bob", bob.owner);
        assertEquals(20, bob.amount);

        //the legacy folder is archived (never deleted) and drained
        assertFalse(legacyFolder.exists(), "the source folder must be archived away");
        File imported1 = new File(legacyFolder.getParentFile(), legacyFolder.getName() + "-Imported");
        assertTrue(imported1.exists(), "the -Imported archive must hold the region files");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Legacy importer idempotency: a re-run never overwrites data already on the backend
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void legacyImporterIsIdempotent() throws Exception {
        storage = Storages.createInMemory();
        storage.init().join();

        File legacyFolder = Files.createTempDirectory("svdata-legacy-idem").toFile();
        File worldFolder = new File(legacyFolder, WORLD);
        worldFolder.mkdirs();
        Files.write(new File(worldFolder, "r.0.0.yml").toPath(), String.join("\n",
                "'0|0':",
                "  '5|64|7':",
                "    owner: alice",
                "    amount: 10",
                "").getBytes(StandardCharsets.UTF_8));

        SVDataManager<Marker> manager = SVDataManager.targeting(Marker.class)
                .on(storage, "svdata_idem")
                .importingLegacyFrom(legacyFolder)
                .build();
        assertEquals(1, manager.importLegacy());

        //a second folder carrying the SAME chunk key with different data must be skipped
        File legacyFolder2 = Files.createTempDirectory("svdata-legacy-idem2").toFile();
        File worldFolder2 = new File(legacyFolder2, WORLD);
        worldFolder2.mkdirs();
        Files.write(new File(worldFolder2, "r.0.0.yml").toPath(), String.join("\n",
                "'0|0':",
                "  '5|64|7':",
                "    owner: imposter",
                "    amount: 999",
                "").getBytes(StandardCharsets.UTF_8));

        SVDataManager<Marker> rerun = SVDataManager.targeting(Marker.class)
                .on(storage, "svdata_idem")
                .importingLegacyFrom(legacyFolder2)
                .build();
        rerun.importLegacy();

        rerun.load();
        Marker value = rerun.getBlockData(WORLD, BlockPos.of(5, 64, 7));
        assertNotNull(value);
        assertEquals("alice", value.owner, "a re-run must never overwrite the existing backend data");
        assertEquals(10, value.amount);
    }
}
