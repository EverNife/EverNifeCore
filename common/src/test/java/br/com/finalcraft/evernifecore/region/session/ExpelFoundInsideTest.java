package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyExitFlag;
import br.com.finalcraft.evernifecore.region.flag.core.EntryDenySettings;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player found inside a {@code deny-entry} region without crossing its border: left there, or sent out. */
class ExpelFoundInsideTest extends TrackerFixture {

    private static final WorldLocPos OUTSIDE = WorldLocPos.of(50.5, 5, 5.5, WORLD);

    private Region arena(EntryDenySettings settings) {
        DenyEntryFlag denyEntry = registered(new DenyEntryFlag());
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyEntry, Tristate.TRUE);
        arena.setFlagSettings(denyEntry, settings);
        return arena;
    }

    private static EntryDenySettings expelling(String revert) {
        EntryDenySettings settings = new EntryDenySettings();
        settings.setExpelFoundInside(true);
        settings.setRevert(RevertStrategy.parse(revert));
        return settings;
    }

    @Test
    void byDefaultAPlayerWhoLogsInInsideStays() {
        Region arena = arena(new EntryDenySettings());

        assertTrue(tracker.onJoin(player, at(5.5)).isAllowed());
        assertEquals(Collections.singletonList(arena), session().getCurrent());
    }

    @Test
    void anExpellingRegionSendsTheLoginToItsFixedPointAndTheTeleportThereCrossesNothing() {
        Region arena = arena(expelling("fixed:50.5,5,5.5"));
        DenyExitFlag denyExit = registered(new DenyExitFlag());
        arena.setFlag(denyExit, Tristate.TRUE);   //would refuse a walk out; the expulsion is not a walk
        List<RegionLeaveEvent> leaves = record(RegionLeaveEvent.class);

        MoveVerdict verdict = tracker.onJoin(player, at(5.5));

        assertEquals(OUTSIDE, verdict.getRevertTo());
        assertTrue(session().getCurrent().isEmpty(), "the tracker counts the player at the destination already");
        assertEquals(1, leaves.size());
        assertEquals(MoveCause.JOIN, leaves.get(0).getCause());
        assertTrue(tracker.onMove(player, at(5.5), OUTSIDE, MoveCause.TELEPORT, null).isAllowed(), "the platform's teleport goes through");
        assertEquals(1, leaves.size(), "and posts no second leave");
    }

    @Test
    void aStrategyThatLooksBackSendsToTheStuckFallback() {
        EntryDenySettings settings = expelling("trail:3");
        settings.setStuckFallback(OUTSIDE);
        arena(settings);

        assertEquals(OUTSIDE, tracker.onJoin(player, at(5.5)).getRevertTo(), "a login has no way back to walk");
    }

    @Test
    void withNoPointAndNoFallbackTheServerSpawnTakesThePlayer() {
        WorldLocPos lobby = WorldLocPos.of(0, 64, 0, "lobby");
        arena(expelling("last-valid"));
        try (ECoreTestWorld ignored = Platforms.lenient().worldSpawn(WORLD, WorldLocPos.of(7, 70, 7, WORLD)).install().withSpawn(() -> lobby)) {
            assertEquals(lobby, tracker.onJoin(player, at(5.5)).getRevertTo());
        }
    }

    @Test
    void aRegionCreatedAroundAStandingPlayerTeleportsThemOut() {
        tracker.onJoin(player, at(5.5));
        DenyEntryFlag denyEntry = registered(new DenyEntryFlag());
        Region arena = TestRegions.box(owner, "arena", WORLD, 0, 0, 0, 10, 10, 10);
        arena.setFlag(denyEntry, Tristate.TRUE);
        arena.setFlagSettings(denyEntry, expelling("fixed:50.5,5,5.5"));

        TestRegions.index(lookup, arena);

        assertEquals(1, player.getTeleports().size());
        assertEquals(OUTSIDE, player.getTeleports().get(0).getWorldLocPos());
        assertTrue(session().getCurrent().isEmpty());
    }

    @Test
    void aRegionThatStartsDenyingWhileThePlayerStandsInsideTeleportsThemOut() {
        tracker.onJoin(player, at(5.5));
        Region arena = arena(expelling("fixed:50.5,5,5.5"));
        assertTrue(player.getTeleports().isEmpty(), "the region was indexed before it denied anything");
        List<RegionLeaveEvent> leaves = record(RegionLeaveEvent.class);

        bus.post(new RegionFlagChangedEvent(arena, null, registeredDenyEntry(), null, null, FlagChangeCause.SET));

        assertEquals(1, player.getTeleports().size());
        assertEquals(OUTSIDE, player.getTeleports().get(0).getWorldLocPos());
        assertEquals(MoveCause.REGION_CHANGED, leaves.get(0).getCause());
    }

    @Test
    void aChangeOfARegionNobodyStandsInTeleportsNobody() {
        tracker.onJoin(player, at(30.5));
        Region arena = arena(expelling("fixed:50.5,5,5.5"));

        bus.post(new RegionFlagChangedEvent(arena, null, registeredDenyEntry(), null, null, FlagChangeCause.SET));

        assertTrue(player.getTeleports().isEmpty());
    }

    private static DenyEntryFlag registeredDenyEntry() {
        return (DenyEntryFlag) FlagRegistry.get().get(FlagKey.of(CoreFlags.OWNER, "deny-entry"));
    }
}
