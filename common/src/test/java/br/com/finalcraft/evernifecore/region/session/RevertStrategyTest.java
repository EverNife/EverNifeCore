package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyEntryFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.MoveDenySettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a denied player goes back to, and the way out for one who keeps trying. */
class RevertStrategyTest extends TrackerFixture {

    private static WorldBlockPos block(int x) {
        return WorldBlockPos.of(x, 5, 5, WORLD);
    }

    @Test
    void theTrailGoesBackAlongTheAcceptedBlocks() {
        RegionPlayerSession session = new RegionPlayerSession(UUID.randomUUID());
        assertNull(session.lastValid());
        for (int x = 1; x <= 5; x++) {
            session.pushAccepted(block(x));
        }

        assertEquals(WorldLocPos.of(5.5, 5, 5.5, WORLD), session.lastValid(), "the center of the last accepted block");
        assertEquals(WorldLocPos.of(3.5, 5, 5.5, WORLD), session.trailBack(2), "two blocks before the last");
        assertEquals(WorldLocPos.of(1.5, 5, 5.5, WORLD), session.trailBack(10), "a short trail gives its oldest block");
    }

    @Test
    void theTrailKeepsTheLastSixteenBlocks() {
        RegionPlayerSession session = new RegionPlayerSession(UUID.randomUUID());
        for (int x = 1; x <= 20; x++) {
            session.pushAccepted(block(x));
        }

        assertEquals(RegionPlayerSession.TRAIL_SIZE, session.getTrailSize());
        assertEquals(WorldLocPos.of(5.5, 5, 5.5, WORLD), session.trailBack(99));
    }

    private Region guarded(MoveDenySettings settings) {
        DenyEntryFlag denyEntry = registered(new DenyEntryFlag(owner));
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyEntry, Tristate.TRUE);
        arena.setFlagSettings(denyEntry, settings);
        return arena;
    }

    /** Walks from 20 to 15 so the trail holds 20..15, then keeps trying to step into the arena. */
    private MoveVerdict tryEntering() {
        return tracker.onMove(player, at(15.5), at(10.5), MoveCause.MOVE);
    }

    private void walkUpTo15() {
        tracker.onJoin(player, at(20.5));
        for (int x = 19; x >= 15; x--) {
            assertTrue(tracker.onMove(player, at(x + 1.5), at(x + 0.5), MoveCause.MOVE).isAllowed());
        }
    }

    @Test
    void trailRevertsStepsBackAlongTheWay() {
        MoveDenySettings settings = new MoveDenySettings();
        settings.setRevert(RevertStrategy.trail(2));
        guarded(settings);
        walkUpTo15();

        assertEquals(at(17.5), tryEntering().getRevertTo());
    }

    @Test
    void allowAfterLetsTheNthDenialInARowThrough() {
        MoveDenySettings settings = new MoveDenySettings();
        settings.setRevert(RevertStrategy.allowAfter(3));
        Region arena = guarded(settings);
        walkUpTo15();

        assertTrue(!tryEntering().isAllowed());
        assertTrue(!tryEntering().isAllowed());
        assertTrue(tryEntering().isAllowed(), "the third denial in a row goes through");
        assertTrue(session().getCurrent().contains(arena), "and the move took effect");
    }

    @Test
    void fixedRevertsToItsPoint() {
        MoveDenySettings settings = new MoveDenySettings();
        WorldLocPos stands = WorldLocPos.of(50, 70, 50, WORLD);
        settings.setRevert(RevertStrategy.fixed(stands));
        guarded(settings);
        walkUpTo15();

        assertEquals(stands, tryEntering().getRevertTo());
    }

    @Test
    void theTextFormRoundTripsAndRefusesNonsenseTeachingTheForms() throws FlagParseException {
        for (RevertStrategy strategy : new RevertStrategy[]{RevertStrategy.lastValid(), RevertStrategy.trail(2), RevertStrategy.allowAfter(5),
                RevertStrategy.fixed(WorldLocPos.of(1, 2, 3, WORLD))}) {
            assertEquals(strategy, RevertStrategy.TYPE.parse(RevertStrategy.TYPE.format(strategy)));
        }
        assertEquals("fixed:world,1,2,3", RevertStrategy.fixed(WorldLocPos.of(1, 2, 3, WORLD)).toText());

        FlagParseException refused = assertThrows(FlagParseException.class, () -> RevertStrategy.TYPE.parse("trail:0"));
        assertTrue(refused.getMessage().contains("at least 1"), refused.getMessage());
        FlagParseException unknown = assertThrows(FlagParseException.class, () -> RevertStrategy.TYPE.parse("teleport-home"));
        assertTrue(unknown.getMessage().contains("last-valid"), unknown.getMessage());
    }

    @Test
    void aPlayerDeniedTwelveTimesInASecondIsMovedBackAlongTheTrailThenToTheFallback() {
        MoveDenySettings settings = new MoveDenySettings();
        WorldLocPos fallback = WorldLocPos.of(0, 100, 0, WORLD);
        settings.setStuckFallback(fallback);
        guarded(settings);
        walkUpTo15();

        for (int denial = 1; denial <= 11; denial++) {
            assertEquals(at(15.5), tryEntering().getRevertTo(), "denial " + denial + " still goes to the last block");
        }
        assertEquals(at(16.5), tryEntering().getRevertTo(), "the 12th moves one block back");
        assertEquals(at(17.5), tryEntering().getRevertTo());
        for (int denial = 14; denial <= 16; denial++) {
            tryEntering();
        }
        assertEquals(fallback, tryEntering().getRevertTo(), "the trail ran out: the fallback");
    }

    @Test
    void denialsSpreadOverSecondsDoNotEscalateAndAnAcceptedMoveResetsTheCount() {
        guarded(new MoveDenySettings());
        walkUpTo15();

        for (int denial = 1; denial <= 12; denial++) {
            assertEquals(at(15.5), tryEntering().getRevertTo());
            clock.advance(Duration.ofMillis(250));
        }
        assertTrue(session().getDenials() < 12);

        assertTrue(tracker.onMove(player, at(15.5), at(14.5), MoveCause.MOVE).isAllowed());
        assertEquals(0, session().getDenials());
    }
}
