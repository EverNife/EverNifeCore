package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.quest.event.ObjectiveCompleteEvent;
import br.com.finalcraft.evernifecore.quest.event.ObjectiveProgressEvent;
import br.com.finalcraft.evernifecore.quest.event.ObjectiveProgressPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestAbandonEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestAbandonPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestCompleteEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestCompletePreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestExpireEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestFailEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestOfferEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestOfferPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestResetEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestStartEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestStartPreEvent;
import br.com.finalcraft.evernifecore.quest.event.StageCompleteEvent;
import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestDefinitionsFixture;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading definitions: the graph checks, the fixtures, the text paths - and the quest events against the native buses. */
class QuestDefinitionsTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
    }

    @AfterEach
    void close() {
        quests.close();
        world.close();
    }

    private IllegalArgumentException refused(String yaml) {
        return assertThrows(IllegalArgumentException.class, () -> quests.parse(yaml));
    }

    @Test
    void thenNamingAMissingStageFailsAtBuild() {
        IllegalArgumentException refused = refused("{id: mine-iron, stages: [{id: mine, then: deliverr, objectives: [{id: o, type: kill}]},"
                + " {id: deliver, objectives: [{id: d, type: kill}]}]}");
        assertTrue(refused.getMessage().contains("deliverr") && refused.getMessage().contains("[mine, deliver]")
                && refused.getMessage().contains("fakequests:mine-iron"), refused.getMessage());
    }

    @Test
    void aQuestWithoutStagesFailsTeachingWhatItNeeds() {
        IllegalArgumentException refused = refused("{id: empty}");
        assertTrue(refused.getMessage().contains("has no stage"), refused.getMessage());
    }

    @Test
    void aGraphWithNoPathToCompleteFails() {
        IllegalArgumentException refused = refused("{id: loop, stages: [{id: a, then: b, objectives: [{id: o, type: kill}]},"
                + " {id: b, then: a, objectives: [{id: p, type: kill}]}]}");
        assertTrue(refused.getMessage().contains("never completes"), refused.getMessage());
    }

    @Test
    void aCycleBetweenStagesWithAWayOutIsAllowed() {
        QuestDefinition definition = quests.parse("{id: loop, stages: [{id: a, then: b, objectives: [{id: o, type: kill}]},"
                + " {id: b, objectives: {mode: any, items: [{id: again, type: kill, then: a}, {id: out, type: break-block, then: COMPLETE}]}}]}");
        assertEquals(2, definition.getStages().size());
    }

    @Test
    void aGoalOnAPayEveryMatchObjectiveFails() {
        IllegalArgumentException refused = refused("{id: job, stages: [{id: s, objectives: [{id: pay, type: break-block, mode: each-match, goal: 10}]}]}");
        assertTrue(refused.getMessage().contains("count until the goal OR pay every match"), refused.getMessage());
    }

    @Test
    void repeatedObjectiveIdsFail() {
        IllegalArgumentException refused = refused("{id: dup, stages: [{id: a, objectives: [{id: o, type: kill}]}, {id: b, objectives: [{id: o, type: kill}]}]}");
        assertTrue(refused.getMessage().contains("two objectives with the id 'o'"), refused.getMessage());
    }

    @Test
    void anUnknownKeyFailsListingTheKeysAQuestReads() {
        IllegalArgumentException refused = refused("{id: x, stagez: [], stages: [{id: a, objectives: [{id: o, type: kill}]}]}");
        assertTrue(refused.getMessage().contains("stagez") && refused.getMessage().contains("stages"), refused.getMessage());
    }

    @Test
    void anUnknownParameterOfTheObjectiveTypeFailsNamingIt() {
        IllegalArgumentException refused = refused("{id: x, stages: [{id: a, objectives: [{id: o, type: break-block, blok: 'minecraft:stone'}]}]}");
        assertTrue(refused.getMessage().contains("blok") && refused.getMessage().contains("objective 'o'"), refused.getMessage());
    }

    @Test
    void aBrokenEffectListNamesItsKey() {
        IllegalArgumentException refused = refused("{id: x, stages: [{id: a, objectives: [{id: o, type: kill}]}],"
                + " on-fail: [{type: message, text: bye}, {type: message}]}");
        assertTrue(refused.getMessage().startsWith("Quest 'fakequests:x' key 'on-fail[1]': "), refused.getMessage());
        assertTrue(refused.getMessage().contains("text"), refused.getMessage());
    }

    @Test
    void anObjectiveErrorNamesItsStage() {
        IllegalArgumentException refused = refused("{id: x, stages: [{id: dig, objectives: [{id: iron, type: kill, mode: bogus}]}]}");
        assertTrue(refused.getMessage().startsWith("Quest 'fakequests:x', stage 'dig', objective 'iron' key 'mode': "), refused.getMessage());
        assertTrue(refused.getMessage().contains("'bogus' is not a progress mode - write count or each-match"), refused.getMessage());
    }

    @Test
    void anObjectiveOfATypeNobodyRegisteredStaysPendingWithoutDroppingTheQuest() {
        QuestDefinition definition = quests.parse("{id: later, stages: [{id: a, objectives: [{id: o, type: 'fakequests:not-yet', goal: 2}]}]}");
        Objective objective = definition.getObjectives().get(0);
        assertEquals("fakequests:not-yet", objective.getPendingType());
        quests.getEngine().register(definition);
        assertNotNull(quests.getEngine().get(quests.id("later")));
    }

    @Test
    void theFixturesRead() {
        QuestDefinition mineIron = QuestDefinitionsFixture.load(quests.getOwner(), "mine-iron");
        assertEquals(Arrays.asList("mine", "deliver"), Arrays.asList(mineIron.getStages().get(0).getId(), mineIron.getStages().get(1).getId()));
        assertEquals(Collections.singleton(quests.id("tutorial")), mineIron.getRequires());
        assertEquals("mining", mineIron.getMeta().getCategory());
        assertEquals(1, mineIron.getGate().getCosts().size());
        QuestDefinition daily = QuestDefinitionsFixture.load(quests.getOwner(), "daily-zombies");
        assertEquals(3, daily.getMeta().getExtra("weight", 1));
        assertTrue(daily.getMeta().getTags().contains("daily"));
        assertNotNull(daily.getRepeat().getWindow());
        QuestDefinition lab = QuestDefinitionsFixture.load(quests.getOwner(), "lab-catch");
        assertEquals(4, lab.getParams().get("type").getValues().size());
        quests.registerFixtures("tutorial", "mine-iron", "daily-zombies");
        assertEquals(3, quests.getEngine().getAll().size());
    }

    @Test
    void textPathsAreTheStableKeysOfALanguageOverlay() {
        QuestDefinition mineIron = QuestDefinitionsFixture.load(quests.getOwner(), "mine-iron");
        assertEquals(Arrays.asList("quests.mine-iron.name", "quests.mine-iron.description",
                "quests.mine-iron.stages.mine.objectives.ore.description", "quests.mine-iron.stages.deliver.objectives.smith.description"),
                QuestDefinitions.textPaths(mineIron));
    }

    @Test
    void paramsReplaceOnlyTheNamesTheyHave() {
        QuestParams params = QuestParams.of("type", "fire");
        assertEquals("Catch fire {other}", params.apply("Catch {type} {other}"));
    }

    @Test
    void questIdsParseBareNamesUnderTheDefaultOwner() {
        assertEquals("fakequests:mine", QuestId.parse("Mine", "FakeQuests").toString());
        assertEquals("other:mine", QuestId.parse("other:mine", "FakeQuests").toString());
        assertThrows(IllegalArgumentException.class, () -> QuestId.parse("a:b:c", "FakeQuests"));
    }

    @Test
    void everyQuestEventConforms() {
        assertEquals(Collections.emptyList(), ECEventConformance.checkAll(QuestOfferPreEvent.class, QuestOfferEvent.class, QuestStartPreEvent.class,
                QuestStartEvent.class, ObjectiveProgressPreEvent.class, ObjectiveProgressEvent.class, ObjectiveCompleteEvent.class,
                StageCompleteEvent.class, QuestCompletePreEvent.class, QuestCompleteEvent.class, QuestFailEvent.class, QuestAbandonPreEvent.class,
                QuestAbandonEvent.class, QuestExpireEvent.class, QuestResetEvent.class));
    }
}
