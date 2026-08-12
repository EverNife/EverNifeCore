package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the cache preset an admin edits does to the store: which shapes are refused outright, when a chunk is
 * read at boot instead of on first touch, what a TTL is allowed to drop - never an unsaved write - and the
 * grid sentinel that refuses a collection written under another chunk size.
 */
@ECoreTest
class SVWorldDataManagerCacheTest extends BlockStoreTestBase {

    private static final String WORLD = "world";

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------------------------------------
    //  Presets this store refuses
    // -----------------------------------------------------------------------------------------------------

    @Test
    void nocacheInThePresetIsRefusedAndPointsAtTheWayOut() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        ConfigSection preset = presetOf("policy: NOCACHE");

        StorageConfigException refused = assertThrows(StorageConfigException.class,
                () -> SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_nocache").cache(preset));

        assertTrue(refused.getMessage().contains("write-back"), refused.getMessage());
        assertTrue(refused.getMessage().contains("TTL"), "the message has to name the shape that DOES work");
        assertTrue(refused.getMessage().contains("ttl-seconds"), refused.getMessage());
    }

    @Test
    void aNoCachePolicyHandedInCodeIsRefusedTheSameWay() {
        ECStorage storage = openStorage(BackendDefinition.memory());

        StorageConfigException refused = assertThrows(StorageConfigException.class,
                () -> SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_nocache_api")
                        .cache(CacheOptions.of(CachePolicy.noCache())));

        assertTrue(refused.getMessage().contains("ttl"), refused.getMessage());
        assertTrue(refused.getMessage().contains("maxSize"), refused.getMessage());
    }

    @Test
    void aMistypedPolicyIsNotOfferedTheValueThisStoreRefuses() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        ConfigSection preset = presetOf("policy: ALWYS");

        StorageConfigException refused = assertThrows(StorageConfigException.class,
                () -> SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_typo").cache(preset));

        assertTrue(refused.getMessage().contains("ALWYS"), refused.getMessage());
        assertFalse(refused.getMessage().contains("NOCACHE"),
                "the underlying failure lists NOCACHE as valid, which is the one value this store rejects");
        assertNotNull(refused.getCause(), "the cause that knows the accepted spellings is kept");
    }

    @Test
    void anUnknownPreloadModeSaysWhatTheThreeModesDo() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        ConfigSection preset = presetOf("preload: SOMETIMES");

        StorageConfigException refused = assertThrows(StorageConfigException.class,
                () -> SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_preload_typo").cache(preset));

        assertTrue(refused.getMessage().contains("SOMETIMES"), refused.getMessage());
        assertTrue(refused.getMessage().contains("AUTO"), refused.getMessage());
    }

    @Test
    void thePresetSeedsItsOwnDefaultsIntoTheAdminSection() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        ConfigSection preset = presetOf("policy: ALWAYS");

        SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_defaults").cache(preset);

        assertEquals(600, preset.getInt("ttl-seconds"));
        assertEquals(0, preset.getInt("max-chunks"), "0 is unbounded");
        assertEquals("AUTO", preset.getString("preload"));
    }

    // -----------------------------------------------------------------------------------------------------
    //  Preload
    // -----------------------------------------------------------------------------------------------------

    @Test
    void autoPreloadsExactlyWhenTheCacheWouldHoldEverythingAnyway() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeTwoChunks(storage, "blocks_auto");

        assertEquals(2, cachedChunksOf(storage, "blocks_auto", presetOf("policy: ALWAYS", "max-chunks: 0")),
                "ALWAYS with no bound: the cache ends up holding the collection, so AUTO reads it at boot");
        assertEquals(0, cachedChunksOf(storage, "blocks_auto", presetOf("policy: TTL", "ttl-seconds: 60")),
                "a TTL cache is not meant to hold the collection, so AUTO leaves the chunks on the backend");
        assertEquals(0, cachedChunksOf(storage, "blocks_auto", presetOf("policy: ALWAYS", "max-chunks: 1")),
                "a bounded cache cannot hold the collection either");
        assertEquals(2, cachedChunksOf(storage, "blocks_auto", presetOf("policy: TTL", "preload: ALWAYS")),
                "ALWAYS overrides the shape of the cache");
        assertEquals(0, cachedChunksOf(storage, "blocks_auto", presetOf("policy: ALWAYS", "preload: NEVER")),
                "and NEVER does too");
    }

    @Test
    void aStoreWithNoCacheSettingAtAllPreloadsItself() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeTwoChunks(storage, "blocks_default_preset");

        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_default_preset");

        assertEquals(2, store.manager().cachedSize(),
                "the default cache is ALWAYS and unbounded, which is exactly the shape AUTO preloads for");
        assertEquals("alice", store.peekBlock(WORLD, BlockPos.of(5, 64, 7)).getOwner(),
                "a preloaded chunk answers a peek, with no read of its own");
    }

    @Test
    void theGridSentinelIsNeverCachedAsAChunk() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeTwoChunks(storage, "blocks_sentinel_cache");

        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_sentinel_cache");

        assertTrue(store.manager().isCached(WorldChunkData.keyOf(WORLD, BlockPos.of(5, 64, 7).getChunkPos())));
        assertFalse(store.manager().isCached(WorldChunkData.META_KEY),
                "no coordinate resolves to the sentinel, and everything walking the cache would have to dodge it");
        assertFalse(store.manager().cachedKeys().contains(WorldChunkData.META_KEY));
    }

    @Test
    void aChunkLeftOnTheBackendIsStillReadOnFirstTouch() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeTwoChunks(storage, "blocks_no_preload");

        SVWorldDataManager<Marker> store = register(SVWorldDataManager.targeting(Marker.class)
                .on(storage, "blocks_no_preload")
                .cache(presetOf("policy: ALWAYS", "preload: NEVER"))
                .autoFlushEvery(Duration.ZERO)
                .build().join());

        assertEquals(0, store.manager().cachedSize());
        assertNull(store.peekBlock(WORLD, BlockPos.of(5, 64, 7)));
        assertEquals("alice", store.getBlock(WORLD, BlockPos.of(5, 64, 7)).join().getOwner());
    }

    @Test
    void aChunkThatDoesNotDecodeIsLeftBehindWhileTheRestLoads() throws IOException {
        Path dataFolder = tempDir.resolve("poisoned");
        ECStorage storage = openStorage(
                BackendDefinition.localFile(dataFolder.toString(), BackendDefinition.FileFormat.YAML));
        storeTwoChunks(storage, "blocks_poison");
        corruptOneStoredChunk(dataFolder);

        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_poison");

        assertEquals(1, store.manager().cachedSize(),
                "the chunk that decoded is in memory; the one that did not is absent from this boot");
        boolean firstSurvived = store.peekBlock(WORLD, BlockPos.of(5, 64, 7)) != null;
        boolean secondSurvived = store.peekBlock(WORLD, BlockPos.of(20, 70, 3)) != null;
        assertTrue(firstSurvived ^ secondSurvived, "exactly one of the two chunks was readable");
    }

    // -----------------------------------------------------------------------------------------------------
    //  TTL
    // -----------------------------------------------------------------------------------------------------

    @Test
    void anExpiredCleanChunkIsDroppedAndReadAgain() throws Exception {
        ECStorage storage = openStorage(BackendDefinition.memory());
        BlockPos pos = BlockPos.of(5, 64, 7);
        SVWorldDataManager<Marker> store = ttlStore(storage, "blocks_ttl");

        store.setBlock(WORLD, pos, new Marker("alice", 10)).join();
        store.flush().join();

        Thread.sleep(80L);
        assertEquals(1, store.manager().purgeExpired(), "a clean chunk nobody touched is collectable");
        assertNull(store.peekBlock(WORLD, pos), "and it is out of the cache");

        assertEquals("alice", store.getBlock(WORLD, pos).join().getOwner(), "the read brings it back");
        assertNotNull(store.peekBlock(WORLD, pos));
    }

    @Test
    void anUnflushedChunkSurvivesTheTtlThePurgeAndAFullCache() throws Exception {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeTwoChunks(storage, "blocks_ttl_dirty");
        BlockPos unsaved = BlockPos.of(500, 64, 500);
        SVWorldDataManager<Marker> store = ttlStore(storage, "blocks_ttl_dirty");

        store.setBlock(WORLD, unsaved, new Marker("carol", 99)).join();
        //two cold reads against a cache bound of one: the unsaved chunk is the eldest, and the only reason
        //it is still here afterwards is the veto that pins a dirty cell
        store.getBlock(WORLD, BlockPos.of(5, 64, 7)).join();
        store.getBlock(WORLD, BlockPos.of(20, 70, 3)).join();

        Thread.sleep(80L);
        store.manager().purgeExpired();

        assertNotNull(store.peekBlock(WORLD, unsaved), "an unsaved write is served even past its TTL");
        assertEquals(1, store.flush().join().getSavedChunks());

        Thread.sleep(80L);
        store.manager().purgeExpired();
        assertNull(store.peekBlock(WORLD, unsaved), "once written, it is as collectable as any other chunk");
        assertEquals("carol", store.getBlock(WORLD, unsaved).join().getOwner(), "and it did land on the backend");
    }

    // -----------------------------------------------------------------------------------------------------
    //  Grid sentinel
    // -----------------------------------------------------------------------------------------------------

    @Test
    void aCollectionWrittenUnderAnotherGridRefusesToOpen() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        int writtenGrid = RegionGridOptions.getCurrent().getChunkSize();
        storeOn(storage, "blocks_grid").close();

        RegionGridOptions previousGrid = RegionGridOptions.getCurrent();
        RegionGridOptions.setCurrent(RegionGridOptions.HYTALE);
        try {
            CompletionException failed = assertThrows(CompletionException.class,
                    () -> SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_grid").build().join());

            StorageConfigException refused = assertInstanceOf(StorageConfigException.class, failed.getCause());
            assertTrue(refused.getMessage().contains(String.valueOf(writtenGrid)), refused.getMessage());
            assertTrue(refused.getMessage().contains(String.valueOf(RegionGridOptions.HYTALE.getChunkSize())),
                    refused.getMessage());
            assertTrue(refused.getMessage().contains(WorldChunkData.META_KEY),
                    "the way out names the entry an operator has to drop");
        } finally {
            RegionGridOptions.setCurrent(previousGrid);
        }

        assertDoesNotThrow(() -> storeOn(storage, "blocks_grid"),
                "the build that failed left no Ref registration behind, so the right store can still open");
    }

    // -----------------------------------------------------------------------------------------------------
    //  Fixtures
    // -----------------------------------------------------------------------------------------------------

    /** Two stored chunks on {@code collection}, left on the backend with nothing cached. */
    private void storeTwoChunks(ECStorage storage, String collection) {
        SVWorldDataManager<Marker> writer = storeOn(storage, collection);
        writer.setBlock(WORLD, BlockPos.of(5, 64, 7), new Marker("alice", 10)).join();
        writer.setBlock(WORLD, BlockPos.of(20, 70, 3), new Marker("bob", 20)).join();
        writer.flush().join();
        writer.close();
    }

    /** Makes one stored chunk unreadable - the grid sentinel is left alone, it is not a chunk. */
    private void corruptOneStoredChunk(Path dataFolder) throws IOException {
        try (Stream<Path> stored = Files.walk(dataFolder)) {
            Path victim = stored.filter(Files::isRegularFile)
                    .filter(file -> !holdsTheSentinel(file))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no chunk was stored under " + dataFolder));
            //an unterminated flow collection: a syntax error, so nothing about the payload's shape matters
            Files.write(victim, "{ unterminated: [flow, collection".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean holdsTheSentinel(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("gridChunkSize");
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    /** How many chunks a store built on {@code preset} has in memory the moment it opens. */
    private int cachedChunksOf(ECStorage storage, String collection, ConfigSection preset) {
        SVWorldDataManager<Marker> store = register(SVWorldDataManager.targeting(Marker.class)
                .on(storage, collection)
                .cache(preset)
                .autoFlushEvery(Duration.ZERO)
                .build().join());
        int cached = store.manager().cachedSize();
        store.close();
        return cached;
    }

    /** A store whose chunks go stale in 50ms and whose cache holds one, so both limits are reachable. */
    private SVWorldDataManager<Marker> ttlStore(ECStorage storage, String collection) {
        return register(SVWorldDataManager.targeting(Marker.class)
                .on(storage, collection)
                .cache(CacheOptions.builder()
                        .policy(CachePolicy.ttl(Duration.ofMillis(50L)))
                        .maxSize(1)
                        .build())
                .autoFlushEvery(Duration.ZERO)
                .build().join());
    }

    /** The section an admin would have edited, as {@code block-cache} of a config file of its own. */
    private ConfigSection presetOf(String... lines) {
        File file = tempDir.resolve("preset_" + System.nanoTime() + ".yml").toFile();
        StringBuilder yaml = new StringBuilder("block-cache:\n");
        for (String line : lines) {
            yaml.append("  ").append(line).append('\n');
        }
        try {
            Files.write(file.toPath(), yaml.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
        Config config = ConfigFactory.open(file);
        return config.getConfigSection("block-cache");
    }
}
