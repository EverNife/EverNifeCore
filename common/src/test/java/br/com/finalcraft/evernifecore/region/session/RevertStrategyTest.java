package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryFlag;
import br.com.finalcraft.evernifecore.region.flag.core.MoveDenySettings;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        DenyEntryFlag denyEntry = registered(new DenyEntryFlag());
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
        assertEquals("fixed:1,2,3,world", RevertStrategy.fixed(WorldLocPos.of(1, 2, 3, WORLD)).toText());

        FlagParseException refused = assertThrows(FlagParseException.class, () -> RevertStrategy.TYPE.parse("trail:0"));
        assertTrue(refused.getMessage().contains("at least 1"), refused.getMessage());
        FlagParseException unknown = assertThrows(FlagParseException.class, () -> RevertStrategy.TYPE.parse("teleport-home"));
        assertTrue(unknown.getMessage().contains("last-valid"), unknown.getMessage());
    }

    @Test
    void spawnAndWorldspawnRoundTrip() throws FlagParseException {
        assertSame(RevertStrategy.spawn(), RevertStrategy.TYPE.parse("spawn"));
        assertSame(RevertStrategy.worldSpawn(), RevertStrategy.TYPE.parse("WorldSpawn"));
        assertEquals("spawn", RevertStrategy.TYPE.format(RevertStrategy.spawn()));
        assertEquals("worldspawn", RevertStrategy.TYPE.format(RevertStrategy.worldSpawn()));
    }

    @Test
    void fixedWithThreeNumbersHasNoWorld() throws FlagParseException {
        RevertStrategy fixed = RevertStrategy.TYPE.parse("fixed:1,2.5,-3");

        assertEquals(RevertStrategy.Kind.FIXED, fixed.getKind());
        assertNull(fixed.getWorld());
        assertNull(fixed.getPoint(), "no world, no point - the player's world fills it in at revert time");
        assertEquals(1, fixed.getX());
        assertEquals(2.5, fixed.getY());
        assertEquals(-3, fixed.getZ());
        assertEquals("fixed:1,2.5,-3", fixed.toText());
    }

    @Test
    void fixedNamesItsWorldLastKeepingItsCase() throws FlagParseException {
        RevertStrategy fixed = RevertStrategy.TYPE.parse("fixed:1,2,3,World_Nether");

        assertEquals("World_Nether", fixed.getWorld());
        assertEquals(WorldLocPos.of(1, 2, 3, "World_Nether"), fixed.getPoint());
        assertEquals(fixed, RevertStrategy.TYPE.parse(fixed.toText()));
    }

    @Test
    void theOldWorldFirstFormIsRefusedListingTheSixForms() {
        FlagParseException refused = assertThrows(FlagParseException.class, () -> RevertStrategy.TYPE.parse("fixed:lobby,1,2,3"));

        for (String form : new String[]{"last-valid", "trail:", "allow-after:", "fixed:<x>,<y>,<z>[,<world>]", "spawn", "worldspawn"}) {
            assertTrue(refused.getMessage().contains(form), form + " missing from: " + refused.getMessage());
        }
    }

    @Test
    void tabCompleteOffersTheSixForms() {
        assertEquals(Arrays.asList("last-valid", "trail:", "allow-after:", "fixed:", "spawn", "worldspawn"), RevertStrategy.TYPE.tabComplete(""));
        assertEquals(Collections.singletonList("worldspawn"), RevertStrategy.TYPE.tabComplete("w"));
    }

    private static MoveDenySettings revertingWith(String strategy) {
        MoveDenySettings settings = new MoveDenySettings();
        settings.setRevert(RevertStrategy.parse(strategy));
        return settings;
    }

    @Test
    void revertSpawnSendsThePlayerToTheRegisteredSpawn() {
        WorldLocPos lobby = WorldLocPos.of(0, 64, 0, "lobby");
        try (ECoreTestWorld ignored = Platforms.lenient().worldSpawn(WORLD, WorldLocPos.of(7, 70, 7, WORLD)).install().withSpawn(() -> lobby)) {
            guarded(revertingWith("spawn"));
            walkUpTo15();

            assertEquals(lobby, tryEntering().getRevertTo());
        }
    }

    @Test
    void revertWorldspawnSendsThePlayerToTheSpawnOfTheWorldItIsIn() {
        WorldLocPos worldSpawn = WorldLocPos.of(7, 70, 7, WORLD);
        try (ECoreTestWorld ignored = Platforms.lenient().worldSpawn(WORLD, worldSpawn).worldSpawn("lobby", WorldLocPos.of(0, 64, 0, "lobby")).install()
                .withSpawn(() -> WorldLocPos.of(0, 64, 0, "lobby"))) {
            guarded(revertingWith("worldspawn"));
            walkUpTo15();

            assertEquals(worldSpawn, tryEntering().getRevertTo(), "the world's own spawn, never the spawn plugin's");
        }
    }

    @Test
    void revertFixedWithoutAWorldUsesThePlayersWorld() {
        guarded(revertingWith("fixed:1,2,3"));
        walkUpTo15();

        assertEquals(WorldLocPos.of(1, 2, 3, WORLD), tryEntering().getRevertTo());
    }

    @Test
    void aSpawnThePlatformDoesNotKnowRevertsToTheLastValidBlock() {
        //the class platform knows no world spawn, and no spawn plugin is registered
        guarded(revertingWith("worldspawn"));
        walkUpTo15();

        assertEquals(at(15.5), tryEntering().getRevertTo());
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
