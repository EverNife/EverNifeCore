package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-shot import of the region-YAML files an older block store left on disk: the same block-to-value
 * associations on the backend, a re-run that overwrites nothing, and a broken file that costs its own blocks
 * and no others.
 */
@ECoreTest
class LegacyWorldDataImportTest extends BlockStoreTestBase {

    private static final String WORLD = "world";
    private static final String TWO_CHUNKS = String.join("\n",
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

    @TempDir
    Path tempDir;

    @Test
    void everyAssociationOfTheRegionFileEndsUpOnTheBackend() {
        File legacyFolder = legacyFolder("first", "r.0.0.yml", TWO_CHUNKS);
        SVWorldDataManager<Marker> store = importingStore(openStorage(BackendDefinition.memory()),
                "blocks_legacy", legacyFolder);

        assertEquals(3, store.importLegacy().join(), "every block value of the file is one import");

        Marker alice = store.getBlock(WORLD, BlockPos.of(5, 64, 7)).join();
        assertEquals("alice", alice.getOwner());
        assertEquals(10, alice.getAmount());
        assertEquals("dave", store.getBlock(WORLD, BlockPos.of(8, 64, 9)).join().getOwner());
        Marker bob = store.getBlock(WORLD, BlockPos.of(20, 70, 3)).join();
        assertEquals("bob", bob.getOwner());
        assertEquals(20, bob.getAmount());

        assertNotNull(store.peekBlock(WORLD, BlockPos.of(5, 64, 7)),
                "an imported chunk is cached too - the store had already read the collection at build");
    }

    @Test
    void theLegacyFolderIsArchivedRatherThanDeleted() {
        File legacyFolder = legacyFolder("archived", "r.0.0.yml", TWO_CHUNKS);
        SVWorldDataManager<Marker> store = importingStore(openStorage(BackendDefinition.memory()),
                "blocks_legacy_archive", legacyFolder);

        store.importLegacy().join();

        assertFalse(legacyFolder.exists(), "the folder is moved away so the next boot does not re-read it");
        File archive = new File(legacyFolder.getParentFile(), legacyFolder.getName() + "-Imported");
        assertTrue(archive.exists());
        assertTrue(new File(new File(archive, WORLD), "r.0.0.yml").exists(), "the region files are kept intact");
    }

    @Test
    void aSecondRunNeverOverwritesWhatIsAlreadyStored() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> first = importingStore(storage, "blocks_legacy_idem",
                legacyFolder("original", "r.0.0.yml", TWO_CHUNKS));
        assertEquals(3, first.importLegacy().join());
        first.close();

        //the same chunk keys, other data: what an operator gets by restoring an old backup into place
        String imposter = String.join("\n",
                "'0|0':",
                "  '5|64|7':",
                "    owner: imposter",
                "    amount: 999",
                "");
        SVWorldDataManager<Marker> rerun = importingStore(storage, "blocks_legacy_idem",
                legacyFolder("rerun", "r.0.0.yml", imposter));

        assertEquals(0, rerun.importLegacy().join(), "a chunk the collection already stores is skipped");
        assertEquals("alice", rerun.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getOwner());
        assertEquals(10, rerun.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getAmount());
    }

    @Test
    void oneRegionFileCostsOneKeyLookupAndOneWrite() {
        ScriptedStorage backend = openScriptedStorage();
        SVWorldDataManager<Marker> store = importingStore(backend, "blocks_legacy_batched",
                legacyFolder("batched", "r.0.0.yml", TWO_CHUNKS));
        backend.resetCalls();

        assertEquals(3, store.importLegacy().join());

        assertEquals(1, backend.callsTo("versions"),
                "one key-only read answers for every chunk of the file, decoding no entity at all");
        assertEquals(1, backend.callsTo("saveAll"));
        assertEquals(0, backend.callsTo("exists"), "asking per chunk would be one round trip per chunk");
        assertEquals(0, backend.callsTo("find"));
    }

    @Test
    void aBrokenRegionFileCostsOnlyItsOwnBlocks() {
        File legacyFolder = legacyFolder("broken", "r.0.0.yml", TWO_CHUNKS);
        writeRegionFile(legacyFolder, "r.1.1.yml", String.join("\n",
                "notachunkposition:",
                "  '5|64|7':",
                "    owner: nobody",
                "    amount: 1",
                ""));
        SVWorldDataManager<Marker> store = importingStore(openStorage(BackendDefinition.memory()),
                "blocks_legacy_broken", legacyFolder);

        assertEquals(3, store.importLegacy().join(), "the readable file imported, the broken one did not abort it");

        assertEquals("alice", store.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getOwner());
        assertFalse(legacyFolder.exists(), "the run finished, so the folder was archived - broken file included");
    }

    @Test
    void aStoreWithNoLegacyFolderImportsNothing() {
        SVWorldDataManager<Marker> store = storeOn(openStorage(BackendDefinition.memory()), "blocks_legacy_none");

        assertEquals(0, store.importLegacy().join());
        assertNull(store.peekBlock(WORLD, BlockPos.of(5, 64, 7)));
    }

    // -----------------------------------------------------------------------------------------------------
    //  Fixtures
    // -----------------------------------------------------------------------------------------------------

    private SVWorldDataManager<Marker> importingStore(ECStorage storage, String collection, File legacyFolder) {
        return register(SVWorldDataManager.targeting(Marker.class)
                .on(storage, collection)
                .importingLegacyFrom(legacyFolder)
                .autoFlushEvery(Duration.ZERO)
                .build().join());
    }

    private SVWorldDataManager<Marker> importingStore(Storage backend, String collection, File legacyFolder) {
        return register(SVWorldDataManager.targeting(Marker.class)
                .on(backend, collection)
                .importingLegacyFrom(legacyFolder)
                .autoFlushEvery(Duration.ZERO)
                .build().join());
    }

    /** A legacy layout: {@code <folder>/<world>/r.X.Z.yml}, the shape the old store wrote. */
    private File legacyFolder(String name, String regionFile, String yaml) {
        File folder = tempDir.resolve(name).toFile();
        writeRegionFile(folder, regionFile, yaml);
        return folder;
    }

    private void writeRegionFile(File legacyFolder, String regionFile, String yaml) {
        File worldFolder = new File(legacyFolder, WORLD);
        worldFolder.mkdirs();
        try {
            Files.write(new File(worldFolder, regionFile).toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + regionFile, e);
        }
    }
}
