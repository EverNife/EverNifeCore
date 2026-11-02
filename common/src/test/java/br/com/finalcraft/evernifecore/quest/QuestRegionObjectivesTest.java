package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.quest.core.ReachLocationObjectiveType;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region objectives - reach-location, stay-in-region, enter-region - over the arena warm-up (fixture D). */
class QuestRegionObjectivesTest {

    private static final WorldLocPos FIELD = WorldLocPos.of(500, 64, 500, "world");
    private static final WorldLocPos ARENA = WorldLocPos.of(5, 64, 5, "world");
    private static final WorldLocPos LOBBY = WorldLocPos.of(205, 64, 205, "world");
    private static final WorldLocPos TARGET = WorldLocPos.of(120, 64, -40, "world");
    private static final WorldLocPos[] CHECKPOINTS = {WorldLocPos.of(305, 64, 5, "world"), WorldLocPos.of(325, 64, 5, "world"),
            WorldLocPos.of(345, 64, 5, "world")};

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;
    private TestFPlayerSender steve;
    private GroupWorld parties;
    private Group party;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        alex = quests.player("Alex");
        steve = quests.player("Steve");
        party = parties.group("alpha", parties.kind("party", GroupKind.Cardinality.ONE));
        party.add(alex.getUniqueId());
        party.add(steve.getUniqueId());
        quests.region("arena", "world", 0, 0, 0, 20, 128, 20);
        quests.region("lobby", "world", 200, 0, 200, 210, 128, 210);
        for (int i = 0; i < CHECKPOINTS.length; i++) {
            int x = (int) CHECKPOINTS[i].getX();
            quests.region("cp" + i, "world", x - 2, 0, 3, x + 2, 128, 7, "checkpoint");
        }
        quests.move(alex, FIELD);
        quests.move(steve, FIELD);
    }

    @AfterEach
    void close() {
        quests.close();
        parties.close();
        world.close();
    }

    private QuestState state(String quest) {
        return engine.getState(quests.owner(alex), quests.id(quest)).orElseThrow(() -> new AssertionError("no state of " + quest));
    }

    private void start(String quest) {
        StartResult result = await(engine.start(quests.owner(alex), quests.id(quest), QuestParams.NONE));
        assertTrue(result.isSuccess(), result.toString());
    }

    private QuestState partyState(String quest) {
        return engine.getState(ProgressOwner.group(party.getId()), quests.id(quest)).orElseThrow(() -> new AssertionError("no party state of " + quest));
    }

    private void registerPartyStay(String extra) {
        engine.register(quests.parse("{id: camp, owner: {type: group}, stages: [{id: s, objectives: [{id: in, type: stay-in-region, region: arena,"
                + " duration: 10s" + extra + "}]}]}"));
        start("camp"); //started by alex, for alex's party
    }

    @Test
    void reachLocationMakesItsTargetRegionOnRegisterAndUnregisterRemovesIt() {
        assertTrue(RegionLookup.get().byTag(ReachLocationObjectiveType.TAG).isEmpty());

        quests.registerFixtures("arena-warmup");

        assertEquals(1, RegionLookup.get().byTag(ReachLocationObjectiveType.TAG).size(), "one TRANSIENT target per reach-location objective");
        assertEquals("quest-arena-warmup-there", RegionLookup.get().byTag(ReachLocationObjectiveType.TAG).iterator().next().getId().getName());

        engine.unregister(quests.id("arena-warmup"));

        assertTrue(RegionLookup.get().byTag(ReachLocationObjectiveType.TAG).isEmpty(), "the target leaves with the quest");
    }

    @Test
    void theArenaWarmupWalksThroughTheTargetTheStayAndTheCheckpoints() {
        quests.registerFixtures("arena-warmup");
        start("arena-warmup");

        quests.move(alex, TARGET);
        assertEquals("hold", state("arena-warmup").getStageId(), "reaching the point closes the first stage");

        for (WorldLocPos checkpoint : CHECKPOINTS) {
            quests.move(alex, checkpoint);
            quests.move(alex, FIELD);
        }
        assertEquals(3, state("arena-warmup").getCount("visit"));
        assertEquals("hold", state("arena-warmup").getStageId(), "the stay is still missing");

        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(61));
        quests.runDue();

        QuestState done = state("arena-warmup");
        assertEquals(QuestStatus.COMPLETED, done.getStatus(), done.toString());
    }

    @Test
    void idlingInTheLobbyFailsTheWarmup() {
        quests.registerFixtures("arena-warmup");
        start("arena-warmup");
        quests.move(alex, LOBBY);
        quests.advance(Duration.ofMinutes(4));
        quests.runDue();
        assertEquals(QuestStatus.ACTIVE, state("arena-warmup").getStatus(), "4 minutes in the lobby are allowed");

        quests.advance(Duration.ofMinutes(2));
        quests.runDue();

        QuestState failed = state("arena-warmup");
        assertEquals(QuestStatus.FAILED, failed.getStatus());
        assertEquals(FailCause.CONDITION, failed.getFailCause());
    }

    @Test
    void stayInRegionContinuousStartsOverOnLeaving() {
        engine.register(quests.parse("{id: hold, stages: [{id: s, objectives: [{id: in, type: stay-in-region, region: arena, duration: 60s}]}]}"));
        start("hold");
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(40));

        quests.move(alex, FIELD);
        assertEquals(0, state("hold").getCount("in"), "leaving starts the stay over");
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(40));
        quests.move(alex, FIELD);
        quests.move(alex, ARENA);
        assertEquals(QuestStatus.ACTIVE, state("hold").getStatus(), "two stays of 40 s are not one of 60 s");

        quests.advance(Duration.ofSeconds(61));
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, state("hold").getStatus());
    }

    @Test
    void stayInRegionAccumulatedAddsTheStaysUp() {
        engine.register(quests.parse("{id: hold, stages: [{id: s, objectives: [{id: in, type: stay-in-region, region: arena, duration: 60s,"
                + " measure: accumulated}]}]}"));
        start("hold");
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(40));
        quests.move(alex, FIELD);
        assertEquals(40, state("hold").getCount("in"));
        quests.advance(Duration.ofMinutes(10));
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(25));

        quests.move(alex, FIELD);

        QuestState done = state("hold");
        assertEquals(QuestStatus.COMPLETED, done.getStatus(), "40 s + 25 s inside");
    }

    @Test
    void leaveRegionCountsEachCrossingOut() {
        engine.register(quests.parse("{id: exits, stages: [{id: s, objectives: [{id: out, type: leave-region, region: arena, goal: 2}]}]}"));
        start("exits");

        quests.move(alex, ARENA);
        quests.move(alex, FIELD);
        assertEquals(1, state("exits").getCount("out"));
        quests.move(alex, ARENA);
        quests.move(alex, FIELD);

        assertEquals(QuestStatus.COMPLETED, state("exits").getStatus());
        assertFalse(engine.getState(ProgressOwner.global(), quests.id("exits")).isPresent());
    }

    @Test
    void stayInRegionClosesWhenTheDurationEndsNotAtTheNextSweep() {
        engine.register(quests.parse("{id: hold, stages: [{id: s, objectives: [{id: in, type: stay-in-region, region: arena, duration: 10s}]}]}"));
        start("hold");
        quests.move(alex, ARENA);

        quests.advance(Duration.ofSeconds(10));
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, state("hold").getStatus(), "the check scheduled on entering ran at 10 s, not the 60 s sweep");
    }

    @Test
    void aStaleTimedCheckDoesNotCloseAStayThatStartedOver() {
        engine.register(quests.parse("{id: hold, stages: [{id: s, objectives: [{id: in, type: stay-in-region, region: arena, duration: 10s}]}]}"));
        start("hold");
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(5));
        quests.move(alex, FIELD);
        quests.move(alex, ARENA);

        quests.advance(Duration.ofSeconds(5));
        quests.runDue();
        assertEquals(QuestStatus.ACTIVE, state("hold").getStatus(), "the 10 s check measured a stay of 5 s");

        quests.advance(Duration.ofSeconds(5));
        quests.runDue();
        assertEquals(QuestStatus.COMPLETED, state("hold").getStatus(), "the stale check scheduled one for the rest of the new stay");
    }

    @Test
    void aGroupStayWaitsForEveryOnlineMemberByDefault() {
        registerPartyStay("");
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(15));
        quests.runDue();
        assertEquals(QuestStatus.ACTIVE, partyState("camp").getStatus(), "steve is still outside");

        quests.move(steve, ARENA);
        quests.advance(Duration.ofSeconds(10));
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, partyState("camp").getStatus(), "both inside for 10 s");
    }

    @Test
    void aGroupStayWithPresenceAnyCountsWhileOneMemberIsInside() {
        registerPartyStay(", presence: any");
        quests.move(alex, ARENA);

        quests.advance(Duration.ofSeconds(10));
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, partyState("camp").getStatus(), "alex alone inside is enough");
    }

    @Test
    void aGroupWithNoOnlineMemberIsNotInside() {
        registerPartyStay(", presence: any, measure: accumulated");
        quests.move(alex, ARENA);
        quests.advance(Duration.ofSeconds(3));
        RegionMovementTracker.get().onQuit(alex); //the platform's quit: alex leaves the arena
        quests.logic().quit(alex);
        quests.logic().quit(steve);

        quests.advance(Duration.ofMinutes(5));
        quests.runDue();

        QuestState camp = partyState("camp");
        assertEquals(QuestStatus.ACTIVE, camp.getStatus(), camp.toString());
        assertEquals(3, camp.getCount("in"), "no member online: the five minutes after alex left added nothing");
    }

    @Test
    void aGroupStayIsMeasuredByTheSweep() {
        engine.register(quests.parse("{id: camp, owner: {type: group}, stages: [{id: s, objectives: [{id: in, type: stay-in-region, region: arena,"
                + " duration: 30s}]}]}"));
        start("camp");
        quests.move(alex, ARENA);
        quests.move(steve, ARENA);

        quests.advance(Duration.ofSeconds(61)); //the party stands still: no crossing after entering
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, partyState("camp").getStatus());
    }
}
