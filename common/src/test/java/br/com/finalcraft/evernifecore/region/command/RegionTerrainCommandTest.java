package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.event.RegionTerrainEvent;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestTerrain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code terrain <region> snapshot|snapshots|restore|undo}: the terrain verbs of a mounted region node. */
class RegionTerrainCommandTest extends RegionCommandFixture {

    private Region arena;
    private Region duelArena;
    private TestTerrain terrain;

    @BeforeEach
    void regions() {
        terrain = harness.platform.getTestTerrain().clear();
        arena = prot.cuboid("arena", WORLD, 0, 0, 0, 3, 4, 3);   //4 x 5 x 4 = 80 positions
        duelArena = duel.cuboid("arena", WORLD, 10, 0, 10, 13, 4, 13);
        steve.grant("fp.region");
        terrain.setBlock(WORLD, 1, 1, 1, "stone");
        terrain.setBlock(WORLD, 11, 1, 11, "stone");
    }

    private FinalCMDPluginCommand mount(RegionCommandNode node) {
        return harness.register(new Fp_Cmd(node));
    }

    private RegionTerrain protTerrain() {
        return prot.getManager().getTerrain();
    }

    /** What a platform listener does on a confirmed change: record the original, then the world changes. */
    private void change(int x, int y, int z, String placed) {
        RegionTerrain.recordChange(WORLD, x, y, z, content -> terrain.captureBlock(WORLD, x, y, z, content));
        terrain.setBlock(WORLD, x, y, z, placed);
    }

    @Test
    void snapshotWritesASnapshotFile() {
        FinalCMDPluginCommand fp = mount(protNode());
        assertEquals(0, protTerrain().getSnapshots(arena).size());

        harness.dispatch(fp, steve, "region terrain arena snapshot");

        assertEquals(1, protTerrain().getSnapshots(arena).size(), steve.getMessages().toString());
        steve.assertAnyMessageContains(protTerrain().getSnapshots(arena).get(0).getName());
    }

    @Test
    void restoreWithoutAnIndexPutsTheNewestSnapshotBack() {
        FinalCMDPluginCommand fp = mount(protNode());
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        terrain.setBlock(WORLD, 1, 1, 1, "dirt");

        harness.dispatch(fp, steve, "region terrain arena restore --confirm");

        assertEquals("stone", terrain.getBlock(WORLD, 1, 1, 1), steve.getMessages().toString());
        steve.assertAnyMessageContains("restored");
    }

    @Test
    void restoreOfAMissingIndexSaysHowManyThereAreAndChangesNothing() {
        FinalCMDPluginCommand fp = mount(protNode());
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        terrain.setBlock(WORLD, 1, 1, 1, "dirt");

        harness.dispatch(fp, steve, "region terrain arena restore 1 --confirm");

        assertEquals("dirt", terrain.getBlock(WORLD, 1, 1, 1));
        assertEquals(0, terrain.getRestores());
        steve.assertAnyMessageContains("has §e1§c snapshot(s), so there is no §e#1§c");
    }

    @Test
    void restoreWithoutConfirmSaysWhichSnapshotAndWritesNothing() {
        FinalCMDPluginCommand fp = mount(protNode());
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        terrain.setBlock(WORLD, 1, 1, 1, "dirt");
        steve.clearMessages();

        harness.dispatch(fp, steve, "region terrain arena restore");

        assertEquals("dirt", terrain.getBlock(WORLD, 1, 1, 1));
        assertEquals(0, terrain.getRestores());
        steve.assertAnyMessageContains("#0");
        steve.assertAnyMessageContains(TestTerrain.DEFAULT_GAME_VERSION);
        steve.assertAnyMessageContains("--confirm");
    }

    @Test
    void undoPutsTheJournalBackInReverseOrderAndEmptiesIt() {
        FinalCMDPluginCommand fp = mount(protNode());
        protTerrain().track(arena);
        terrain.setBlock(WORLD, 0, 1, 0, "log");
        terrain.setBlock(WORLD, 2, 1, 2, "glass");
        change(0, 1, 0, "air");
        change(1, 1, 1, "air");
        change(2, 1, 2, "dirt");

        harness.dispatch(fp, steve, "region terrain arena undo");

        assertEquals(Arrays.asList(WORLD + ":2,1,2", WORLD + ":1,1,1", WORLD + ":0,1,0"), terrain.getRestoredPositions());
        assertEquals("log", terrain.getBlock(WORLD, 0, 1, 0));
        assertEquals("stone", terrain.getBlock(WORLD, 1, 1, 1));
        assertEquals("glass", terrain.getBlock(WORLD, 2, 1, 2));
        assertEquals(0, protTerrain().getJournalSize(arena));
        steve.assertAnyMessageContains("undone");
    }

    @Test
    void snapshotsListsEachIndexItsGameVersionAndTheJournal() {
        FinalCMDPluginCommand fp = mount(protNode());
        protTerrain().track(arena);
        change(1, 1, 1, "air");
        change(2, 2, 2, "dirt");
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        steve.clearMessages();

        harness.dispatch(fp, steve, "region terrain arena snapshots");

        steve.assertAnyMessageContains("track-changes §eon");
        steve.assertAnyMessageContains("journal §e2§6");
        steve.assertAnyMessageContains("§e2§6 snapshot(s)");
        steve.assertAnyMessageContains("#0");
        steve.assertAnyMessageContains("#1");
        steve.assertAnyMessageContains("game version §f" + TestTerrain.DEFAULT_GAME_VERSION);
    }

    @Test
    void snapshotsDatesEachSnapshotByTheStampInItsNameNotByTheFilesMtime() {
        FinalCMDPluginCommand fp = mount(protNode());
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        File snapshot = protTerrain().getSnapshot(arena, 0);
        assertTrue(snapshot.setLastModified(978307200000L), "a copy that lost its dates, set to 2001-01-01");
        steve.clearMessages();

        harness.dispatch(fp, steve, "region terrain arena snapshots");

        String taken = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(RegionTerrain.getSnapshotTakenAt(snapshot)));
        steve.assertAnyMessageContains("#0 §f" + taken);
        assertFalse(steve.getMessages().toString().contains("2001-"), steve.getMessages().toString());
    }

    @Test
    void withoutTheRestorePermissionTheVerbDeniesAndNothingIsRestored() {
        FinalCMDPluginCommand fp = mount(new RegionCommandNode(prot.getManager(), SelectionSpec.cuboid(), "fp.region"));
        TestFPlayerSender moderator = Senders.player("Moderator").grant("fp.region").grant("fp.region.snapshot");
        harness.dispatch(fp, moderator, "region terrain arena snapshot");
        assertEquals(1, protTerrain().getSnapshots(arena).size(), moderator.getMessages().toString());
        terrain.setBlock(WORLD, 1, 1, 1, "dirt");

        harness.dispatch(fp, moderator, "region terrain arena restore --confirm");

        assertEquals("dirt", terrain.getBlock(WORLD, 1, 1, 1));
        assertEquals(0, terrain.getRestores());
        moderator.assertAnyMessageContains("fp.region.restore");
    }

    @Test
    void anotherOwnersRegionWithoutForeignAccessIsRefusedAndItsTerrainStaysIntact() {
        FinalCMDPluginCommand fp = mount(protNode());
        RegionTerrain duelTerrain = duel.getManager().getTerrain();
        duelTerrain.snapshot(duelArena).join();
        terrain.setBlock(WORLD, 11, 1, 11, "dirt");

        harness.dispatch(fp, steve, "region terrain theduel:arena restore --confirm");
        harness.dispatch(fp, steve, "region terrain theduel:arena snapshot");
        harness.dispatch(fp, steve, "region terrain theduel:arena undo");

        assertEquals("dirt", terrain.getBlock(WORLD, 11, 1, 11));
        assertEquals(0, terrain.getRestores());
        assertEquals(1, duelTerrain.getSnapshots(duelArena).size());
        steve.assertAnyMessageContains("belongs to §etheduel");
    }

    @Test
    void theRestoreWalksTheNodesBudgetPerTick() {
        RegionCommandNode node = protNode().setTerrainBudget(10);
        FinalCMDPluginCommand fp = mount(node);
        harness.dispatch(fp, steve, "region terrain arena snapshot");
        AtomicInteger ticks = new AtomicInteger();
        ECEventSubscription<RegionTerrainEvent> subscription = ECEventBus.global().subscribe(RegionTerrainEvent.class, event -> {
            if (event.getType() == RegionTerrainEvent.Type.RESTORE_TICK) ticks.incrementAndGet();
        });
        try {
            harness.dispatch(fp, steve, "region terrain arena restore --confirm");
        } finally {
            subscription.unsubscribe();
        }

        assertEquals(8, ticks.get(), "80 positions at 10 per tick: " + steve.getMessages());
        assertEquals(80, terrain.getRestores());
    }

    @Test
    void aBudgetBelowOneIsRefused() {
        RegionCommandNode node = protNode();
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> node.setTerrainBudget(0));
        assertEquals(RegionCommandNode.DEFAULT_TERRAIN_BUDGET, node.getTerrainBudget());
        assertEquals(true, refused.getMessage().contains("1 or more"), refused.getMessage());
    }
}
