package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.event.RegionBoundaryEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyExitFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyTeleportInFlag;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crossing region borders: the flags decide, leaves go before enters, and only an allowed move changes the session. */
class RegionMovementTrackerTest extends TrackerFixture {

    @Test
    void aMoveInsideTheSameBlockIsAllowedAtOnce() {
        region("arena", 0, 10);
        tracker.onJoin(player, at(5.2));

        assertSame(MoveVerdict.allowed(), tracker.onMove(player, at(5.2), at(5.8), MoveCause.MOVE));
    }

    @Test
    void aDeniedEntryRevertsToTheCenterOfTheLastBlockAndChangesNothing() {
        Region arena = region("arena", 0, 10);
        arena.setFlag(registered(new DenyEntryFlag()), Tristate.TRUE);
        List<RegionEnterEvent> enters = record(RegionEnterEvent.class);

        MoveVerdict verdict = walk(12.7, 10.5);

        assertEquals(at(12.5), verdict.getRevertTo());
        assertTrue(session().getCurrent().isEmpty(), "a denied move leaves the session where it was");
        assertTrue(enters.isEmpty(), "a denied move posts nothing");
    }

    @Test
    void denyExitStopsOnlyLeavingAndDenyEntryLetsThoseInsideWalk() {
        Region arena = region("arena", 0, 10);
        arena.setFlag(registered(new DenyExitFlag()), Tristate.TRUE);
        arena.setFlag(registered(new DenyEntryFlag()), Tristate.TRUE);

        assertTrue(walk(5.5, 7.5).isAllowed(), "moving inside crosses nothing");
        MoveVerdict leaving = tracker.onMove(player, at(7.5), at(12.5), MoveCause.MOVE);

        assertEquals(at(7.5), leaving.getRevertTo());
        assertEquals(Collections.singletonList(arena), session().getCurrent());
    }

    @Test
    void denyTeleportInStopsTeleportsAndLetsWalkersIn() {
        Region arena = region("arena", 0, 10);
        arena.setFlag(registered(new DenyTeleportInFlag()), Tristate.TRUE);
        tracker.onJoin(player, at(20.5));

        assertTrue(!tracker.onMove(player, at(20.5), at(5.5), MoveCause.TELEPORT).isAllowed());
        assertTrue(tracker.onMove(player, at(20.5), at(5.5), MoveCause.MOVE).isAllowed());
        assertEquals(Collections.singletonList(arena), session().getCurrent());
    }

    @Test
    void crossingStraightFromAToBLeavesABeforeEnteringB() {
        Region a = region("a", 0, 10);
        Region b = region("b", 11, 20);
        List<RegionBoundaryEvent> crossings = record(RegionBoundaryEvent.class);
        tracker.onJoin(player, at(5.5));
        crossings.clear();

        tracker.onMove(player, at(10.5), at(11.5), MoveCause.MOVE);

        assertEquals(2, crossings.size());
        assertTrue(crossings.get(0) instanceof RegionLeaveEvent && crossings.get(0).getRegion() == a);
        assertTrue(crossings.get(1) instanceof RegionEnterEvent && crossings.get(1).getRegion() == b);
    }

    @Test
    void aCancelledEnterRevertsToTheLastValidBlock() {
        region("arena", 0, 10);
        bus.subscribe(RegionEnterEvent.class, event -> event.setCancelled(true));

        MoveVerdict verdict = walk(12.5, 9.5);

        assertEquals(at(12.5), verdict.getRevertTo());
        assertTrue(session().getCurrent().isEmpty());
    }

    @Test
    void joiningInsideDenyEntryEntersUnasked() {
        Region arena = region("arena", 0, 10);
        arena.setFlag(registered(new DenyEntryFlag()), Tristate.TRUE);
        List<RegionEnterEvent> enters = record(RegionEnterEvent.class);

        tracker.onJoin(player, at(5.5));

        assertEquals(1, enters.size());
        assertEquals(MoveCause.JOIN, enters.get(0).getCause());
        assertEquals(Collections.singletonList(arena), session().getCurrent());
    }

    @Test
    void quittingLeavesEveryRegionOnceAndForgetsTheSession() {
        Region arena = region("arena", 0, 10);
        tracker.onJoin(player, at(5.5));
        List<RegionLeaveEvent> leaves = record(RegionLeaveEvent.class);

        tracker.onQuit(player);
        tracker.onQuit(player);

        assertEquals(1, leaves.size());
        assertSame(arena, leaves.get(0).getRegion());
        assertEquals(MoveCause.QUIT, leaves.get(0).getCause());
        assertNull(session());
    }

    @Test
    void theCoreQuitEventEndsTheSession() {
        region("arena", 0, 10);
        tracker.onJoin(player, at(5.5));

        bus.post(new ECPlayerQuitEvent(new PlayerData(player.getUniqueId(), player.getName())));

        assertNull(session());
    }

    @Test
    void regionsAppearingAndGoingUnderAStillPlayerAreCrossedOnTheirWorldsThread(TestPlatform platform) {
        Region far = region("far", 100, 110);
        tracker.onJoin(player, at(5.5));
        List<RegionBoundaryEvent> crossings = record(RegionBoundaryEvent.class);
        platform.reset();

        Region added = region("added", 0, 10);
        TestRegions.reshapeAndRebuild(lookup, far, CuboidShape.of(BlockPos.of(3, 0, 0), BlockPos.of(8, 10, 10)));
        TestRegions.unindex(lookup, added);

        assertEquals(3, crossings.size());
        assertEquals(Arrays.asList(MoveCause.REGION_CREATED, MoveCause.REGION_CREATED, MoveCause.REGION_REMOVED),
                Arrays.asList(crossings.get(0).getCause(), crossings.get(1).getCause(), crossings.get(2).getCause()));
        assertSame(far, crossings.get(1).getRegion(), "the rebuild of the world moved it under the player");
        assertEquals(Arrays.asList(WORLD, WORLD, WORLD), platform.getWorldThreadRuns());
        assertEquals(0, platform.getMainThreadRuns());
        assertEquals(Collections.singletonList(far), session().getCurrent());
    }

    @Test
    void aCallOffTheWorldsThreadIsLoggedOnceAndRunsAnyway() {
        region("arena", 0, 10);
        try (ECoreTestWorld offThread = Platforms.lenient().offWorldThread().install()) {
            tracker.onJoin(player, at(20.5));
            assertTrue(tracker.onMove(player, at(20.5), at(5.5), MoveCause.MOVE).isAllowed());
            tracker.onMove(player, at(5.5), at(6.5), MoveCause.MOVE);
        }

        assertTrue(tracker.hasWarned("onMove"));
        assertTrue(tracker.hasWarned("onJoin"));
    }

    @Test
    void withNobodyListeningNoCrossingEventIsBuilt() {
        RecordingAudience nativeSide = new RecordingAudience();
        nativeSide.setHasListeners(false);
        bus.addNativeAudience(nativeSide);
        region("arena", 0, 10);

        walk(20.5, 5.5);

        assertTrue(nativeSide.getGateChecks() > 0, "the bus was asked whether anyone listens");
        assertTrue(nativeSide.getDispatched().isEmpty(), "and with nobody listening, nothing was built");
    }
}
