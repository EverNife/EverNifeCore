package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.region.event.RegionTerrainEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.TrackChangesSettings;
import br.com.finalcraft.evernifecore.region.terrain.JournalPersistence;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.TestTerrain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The change journal of a track-changes region, through the in-memory terrain double. */
class RegionTerrainTest extends ManagedRegionFixture {

    private ECPluginData core;

    @BeforeEach
    void registerCoreFlags(TestPlatform platform) {
        platform.getTestTerrain().clear();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
    }

    @AfterEach
    void unregisterCoreFlags() {
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
    }

    @Test
    void trackChangesIsACoreFlag() {
        assertTrue(CoreFlags.all().contains(CoreFlags.TRACK_CHANGES));
        assertEquals("track-changes", CoreFlags.TRACK_CHANGES.getKey().getName());
    }

    @Test
    void theFirstChangeOfAPositionWinsAndTheRestorePutsTheOriginalBack(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 1, 10, 1, "stone");
        Region region = trackedRegion();

        breakThenPlace(terrain, 1, 10, 1, "dirt");
        breakThenPlace(terrain, 1, 10, 1, "glass");

        assertEquals(1, region.getManager().getTerrain().getJournalSize(region));
        region.getManager().getTerrain().restoreJournal(region, 10).join();
        assertEquals("stone", terrain.getBlock(WORLD, 1, 10, 1));
        assertEquals(0, region.getManager().getTerrain().getJournalSize(region));
    }

    @Test
    void aChangeOutsideAnyTrackingRegionIsNotRecorded(TestPlatform platform) {
        Region region = trackedRegion();
        RegionTerrain.recordChange(WORLD, 500, 10, 500, content -> "never read");
        assertEquals(0, region.getManager().getTerrain().getJournalSize(region));
    }

    @Test
    void aBudgetedRestoreTakesCeilOfNOverBStepsInReverseOrder(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion();
        int entries = 7;
        for (int x = 0; x < entries; x++) {
            terrain.setBlock(WORLD, x, 10, 0, "original-" + x);
            breakThenPlace(terrain, x, 10, 0, "changed");
        }
        int before = platform.getWorldThreadRuns().size();

        int restored = region.getManager().getTerrain().restoreJournal(region, 3).join();

        assertEquals(entries, restored);
        assertEquals(3, platform.getWorldThreadRuns().size() - before, "ceil(7 / 3) = 3 steps on the world thread");
        List<String> reverse = new ArrayList<>();
        for (int x = entries - 1; x >= 0; x--) {
            reverse.add(WORLD + ":" + x + ",10,0");
            assertEquals("original-" + x, terrain.getBlock(WORLD, x, 10, 0));
        }
        assertEquals(reverse, terrain.getRestoredPositions(), "the most recent first change goes back first");
        assertEquals(0, region.getManager().getTerrain().getJournalSize(region));
    }

    @Test
    void anInterruptedRestoreLeavesOnlyWhatWasNotRestoredInTheJournal(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion();
        for (int x = 0; x < 5; x++) {
            int at = x;
            RegionTerrain.recordChange(WORLD, x, 10, 0, content -> "original-" + at);
        }
        terrain.failAfterRestores(2);

        assertThrows(CompletionException.class, () -> region.getManager().getTerrain().restoreJournal(region, 10).join());

        assertEquals(3, region.getManager().getTerrain().getJournalSize(region), "2 of 5 went back; 3 stay for the next restore");
        terrain.failAfterRestores(-1);
        assertEquals(3, region.getManager().getTerrain().restoreJournal(region, 10).join());
        assertEquals("original-0", terrain.getBlock(WORLD, 0, 10, 0));
    }

    @Test
    void aDiskJournalSurvivesClosingAndReopeningTheManager(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 2, 10, 2, "stone");
        RegionManager first = manager();
        Region region = created(first.create("arena", WORLD, box(0, 0, 9, 9)));
        first.getTerrain().track(region, new TrackChangesSettings().setPersistence(JournalPersistence.DISK));
        breakThenPlace(terrain, 2, 10, 2, "dirt");
        first.flush().join();
        first.close().join();

        RegionManager second = manager();
        Region reloaded = second.get("arena");
        assertEquals(1, second.getTerrain().getJournalSize(reloaded));
        second.getTerrain().restoreJournal(reloaded, 10).join();
        assertEquals("stone", terrain.getBlock(WORLD, 2, 10, 2));
    }

    @Test
    void aMemoryJournalByDefaultWritesNoFileAndIsGoneAfterReopening(TestPlatform platform) throws Exception {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 2, 10, 2, "stone");
        RegionManager first = manager();
        Region region = created(first.create("arena", WORLD, box(0, 0, 9, 9)));
        first.getTerrain().track(region);   //no option: the default
        breakThenPlace(terrain, 2, 10, 2, "dirt");
        first.flush().join();
        first.close().join();

        assertEquals(Collections.emptyList(), filesUnder(new File(owner.getMetaInfo().getDataFolder(), "regions/terrain").toPath()));
        RegionManager second = manager();
        assertEquals(0, second.getTerrain().getJournalSize(second.get("arena")));
    }

    @Test
    void byDefaultTheBlockItsNbtAndTheEntityAllComeBack(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion();
        restoredAfterChanges(terrain, region);

        assertEquals("chest", terrain.getBlock(WORLD, 3, 10, 3));
        assertEquals("items=diamond", terrain.getNbt(WORLD, 3, 10, 3));
        assertTrue(terrain.getEntities(WORLD).contains(TestTerrain.entityState("armor_stand", 4, 10, 4)));
    }

    @Test
    void withNbtOffTheTileEntityComesBackEmpty(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion(new TrackChangesSettings().setContent(TerrainContent.of(true, false, true)));
        restoredAfterChanges(terrain, region);

        assertEquals("chest", terrain.getBlock(WORLD, 3, 10, 3));
        assertNull(terrain.getNbt(WORLD, 3, 10, 3), "the chest is back without its items");
    }

    @Test
    void withBlocksOffNothingIsRecordedInTheJournal(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 2, 10, 2, "stone");
        Region region = trackedRegion(new TrackChangesSettings().setContent(TerrainContent.of(false, true, true)));

        breakThenPlace(terrain, 2, 10, 2, "air");

        assertEquals(0, region.getManager().getTerrain().getJournalSize(region));
        assertEquals(0, region.getManager().getTerrain().restoreJournal(region, 10).join());
        assertEquals("air", terrain.getBlock(WORLD, 2, 10, 2), "a journal that keeps no blocks puts none back");
    }

    @Test
    void aSnapshotFromAnotherGameVersionIsRefusedNamingTheWayOut(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 0, 5, 0, "stone");
        RegionManager manager = manager();
        Region region = created(manager.create("small", WORLD, box(0, 0, 1, 1)));
        manager.getTerrain().snapshot(region).join();
        terrain.setBlock(WORLD, 0, 5, 0, "air");

        terrain.setGameVersion("test-2");
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> manager.getTerrain().restoreSnapshot(region, 0, 100));

        assertTrue(refused.getMessage().contains(TestTerrain.DEFAULT_GAME_VERSION) && refused.getMessage().contains("test-2"), refused.getMessage());
        assertTrue(refused.getMessage().contains("take a new snapshot"), refused.getMessage());
        assertEquals("air", terrain.getBlock(WORLD, 0, 5, 0), "nothing was put back");

        terrain.setGameVersion(TestTerrain.DEFAULT_GAME_VERSION);
        manager.getTerrain().restoreSnapshot(region, 0, 100).join();
        assertEquals("stone", terrain.getBlock(WORLD, 0, 5, 0), "the version that took it restores it");
    }

    @Test
    void withEntitiesOffTheEntityDoesNotComeBack(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion(new TrackChangesSettings().setContent(TerrainContent.of(true, true, false)));
        restoredAfterChanges(terrain, region);

        assertEquals("chest", terrain.getBlock(WORLD, 3, 10, 3));
        assertFalse(terrain.getEntities(WORLD).contains(TestTerrain.entityState("armor_stand", 4, 10, 4)));
    }

    // ------------------------------------------------------------------

    @Test
    void nPlusOneSnapshotsLeaveTheNNewestFiles(TestPlatform platform) {
        RegionManager manager = manager();
        Region region = created(manager.create("small", WORLD, box(0, 0, 1, 1)));
        RegionTerrain terrain = manager.getTerrain();
        terrain.setSnapshotRetention(3);

        List<File> taken = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            taken.add(terrain.snapshot(region).join());
        }

        List<File> kept = terrain.getSnapshots(region);
        assertEquals(3, kept.size(), "retention 3 keeps three of four: " + kept);
        assertEquals(taken.subList(1, 4), reversed(kept), "the newest three stay, the oldest is gone");
        assertFalse(taken.get(0).exists());
    }

    @Test
    void aSnapshotRestoreBringsTheBoxBackABudgetAtATime(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 0, 5, 0, "chest", "items=diamond");
        terrain.spawn(WORLD, "armor_stand", 1, 5, 1);
        RegionManager manager = manager();
        Region region = created(manager.create("small", WORLD, box(0, 0, 1, 1)));
        manager.getTerrain().snapshot(region).join();

        terrain.setBlock(WORLD, 0, 5, 0, "air");
        terrain.kill(WORLD, "armor_stand", 1, 5, 1);
        terrain.setBlock(WORLD, 1, 5, 1, "dirt");
        manager.getTerrain().restoreSnapshot(region, 0, 100).join();

        assertEquals("chest", terrain.getBlock(WORLD, 0, 5, 0));
        assertEquals("items=diamond", terrain.getNbt(WORLD, 0, 5, 0));
        assertEquals("air", terrain.getBlock(WORLD, 1, 5, 1), "what was built after the snapshot is cleared");
        assertTrue(terrain.getEntities(WORLD).contains("armor_stand@1,5,1"));
    }

    @Test
    void theJournalAndTheSnapshotPostTheirTerrainEvents(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        terrain.setBlock(WORLD, 1, 10, 1, "stone");
        Region region = trackedRegion();
        List<String> seen = new ArrayList<>();
        ECEventSubscription<RegionTerrainEvent> subscription = ECEventBus.global().subscribe(RegionTerrainEvent.class,
                event -> seen.add(event.getType() + (event.isJournal() ? " journal" : " snapshot")));
        try {
            breakThenPlace(terrain, 1, 10, 1, "dirt");
            region.getManager().getTerrain().restoreJournal(region, 10).join();
            region.getManager().getTerrain().snapshot(region).join();
        } finally {
            subscription.unsubscribe();
        }

        assertEquals(Arrays.asList("RESTORE_START journal", "RESTORE_TICK journal", "RESTORE_END journal", "SNAPSHOT snapshot"), seen);
    }

    @Test
    void aRestoreRunsItsFirstTickNowAndEachNextOneATickLater(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion();
        for (int x = 0; x < 3; x++) {
            terrain.setBlock(WORLD, x, 10, 1, "stone");
            breakThenPlace(terrain, x, 10, 1, "air");
        }
        platform.getWorldThreadRuns().clear();
        platform.getWorldThreadNextTickRuns().clear();

        region.getManager().getTerrain().restoreJournal(region, 1).join();

        assertEquals(Arrays.asList(WORLD, WORLD, WORLD), platform.getWorldThreadRuns(), "one world-thread run per tick");
        assertEquals(Arrays.asList(WORLD, WORLD), platform.getWorldThreadNextTickRuns(), "every tick after the first waits for the next one");
    }

    @Test
    void eachRestoreTickReportsWhatItPutBackAndHowLongItTook(TestPlatform platform) {
        TestTerrain terrain = platform.getTestTerrain();
        Region region = trackedRegion();
        for (int x = 0; x < 3; x++) {
            terrain.setBlock(WORLD, x, 10, 1, "stone");
            breakThenPlace(terrain, x, 10, 1, "air");
        }
        List<RegionTerrainEvent> ticks = new ArrayList<>();
        List<RegionTerrainEvent> snapshots = new ArrayList<>();
        ECEventSubscription<RegionTerrainEvent> subscription = ECEventBus.global().subscribe(RegionTerrainEvent.class, event -> {
            if (event.getType() == RegionTerrainEvent.Type.RESTORE_TICK) ticks.add(event);
            if (event.getType() == RegionTerrainEvent.Type.SNAPSHOT) snapshots.add(event);
        });
        try {
            region.getManager().getTerrain().restoreJournal(region, 2).join();
            region.getManager().getTerrain().snapshot(region).join();
        } finally {
            subscription.unsubscribe();
        }

        assertEquals(Arrays.asList(2, 1), ticks.stream().map(RegionTerrainEvent::getBlocks).collect(Collectors.toList()));
        assertTrue(ticks.stream().allMatch(tick -> tick.isJournal() && tick.getNanos() > 0), "every tick timed its placing");
        assertEquals(1, snapshots.size());
        assertTrue(snapshots.get(0).getNanos() > 0, "the snapshot timed its write");
    }

    private static List<File> reversed(List<File> files) {
        List<File> copy = new ArrayList<>(files);
        Collections.reverse(copy);
        return copy;
    }

    private Region trackedRegion() {
        return trackedRegion(null);
    }

    private Region trackedRegion(TrackChangesSettings settings) {
        RegionManager manager = manager();
        Region region = created(manager.create("arena", WORLD, box(0, 0, 9, 9)));
        if (settings == null) {
            manager.getTerrain().track(region);
        } else {
            manager.getTerrain().track(region, settings);
        }
        return region;
    }

    /** What a platform listener does on a confirmed change: record the original, then the world changes. */
    private static void breakThenPlace(TestTerrain terrain, int x, int y, int z, String placed) {
        RegionTerrain.recordChange(WORLD, x, y, z, content -> terrain.captureBlock(WORLD, x, y, z, content));
        terrain.setBlock(WORLD, x, y, z, placed);
    }

    /** A chest with items broken and an armor stand knocked down, then the journal restored. */
    private static void restoredAfterChanges(TestTerrain terrain, Region region) {
        terrain.setBlock(WORLD, 3, 10, 3, "chest", "items=diamond");
        terrain.spawn(WORLD, "armor_stand", 4, 10, 4);

        breakThenPlace(terrain, 3, 10, 3, "air");
        RegionTerrain.recordEntityRemoval(WORLD, 4, 10, 4, content -> TestTerrain.entityState("armor_stand", 4, 10, 4));
        terrain.kill(WORLD, "armor_stand", 4, 10, 4);

        region.getManager().getTerrain().restoreJournal(region, 10).join();
    }

    private static List<Path> filesUnder(Path root) throws Exception {
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }
}
