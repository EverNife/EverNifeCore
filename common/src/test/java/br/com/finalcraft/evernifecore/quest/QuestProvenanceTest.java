package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code provenance:} test of an objective, read from the engine owner's tracker. */
class QuestProvenanceTest {

    private static final String NATURAL_ORE = "{id: ores, stages: [{id: s, objectives: [{id: ore, type: break-block,"
            + " block: \"minecraft:iron_ore\", goal: 5, provenance: {natural: true}}]}]}";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        alex = quests.player("Alex");
    }

    @AfterEach
    void close() {
        quests.close();
        world.close();
    }

    private long count(String quest, String objective) {
        return engine.getState(quests.owner(alex), quests.id(quest)).orElseThrow(AssertionError::new).objective(objective).getCount();
    }

    private void start(String quest) {
        StartResult result = await(engine.start(quests.owner(alex), quests.id(quest), QuestParams.NONE));
        assertTrue(result.isSuccess(), result.toString());
    }

    @Test
    void aNaturalOnlyObjectiveSkipsTheBlocksThePlayerPlaced() {
        engine.register(quests.parse(NATURAL_ORE));
        start("ores");

        quests.post(quests.contexts().place(alex, "world", 1, 64, 1, "minecraft:iron_ore"));
        quests.post(quests.contexts().breakBlock(alex, "world", 1, 64, 1, "minecraft:iron_ore"));
        assertEquals(0, count("ores", "ore"), "the ore Alex placed is not natural");

        quests.post(quests.contexts().breakBlock(alex, "world", 2, 64, 2, "minecraft:iron_ore"));
        quests.post(quests.contexts().breakBlock(alex, "world", 1, 64, 1, "minecraft:iron_ore"));
        assertEquals(2, count("ores", "ore"), "untouched ore counts, and so does the placed spot once its record left with the break");
    }

    @Test
    void spawnerFalseLeavesSpawnerCreaturesOut() {
        engine.register(quests.parse("{id: hunt, stages: [{id: s, objectives: [{id: z, type: kill, entity: \"minecraft:zombie\", goal: 5,"
                + " provenance: {spawner: false}}]}]}"));
        start("hunt");
        UUID caged = UUID.randomUUID();
        UUID wild = UUID.randomUUID();

        quests.post(quests.contexts().spawn(caged, "minecraft:zombie", "SPAWNER", "world"));
        quests.post(quests.contexts().kill(alex, caged, "minecraft:zombie"));
        quests.post(quests.contexts().kill(alex, wild, "minecraft:zombie"));

        assertEquals(1, count("hunt", "z"), "only the zombie no spawner made counts");
    }

    @Test
    void ownDropFalseLeavesOutWhatThePlayerDroppedThemselves() {
        engine.register(quests.parse("{id: gather, stages: [{id: s, objectives: [{id: d, type: pickup, item: \"minecraft:diamond\", goal: 5,"
                + " provenance: {own-drop: false}}]}]}"));
        start("gather");
        UUID dropped = UUID.randomUUID();

        quests.post(quests.contexts().drop(alex, dropped, "minecraft:diamond"));
        quests.post(quests.contexts().pickup(alex, dropped, "minecraft:diamond"));
        quests.post(quests.contexts().pickup(alex, UUID.randomUUID(), "minecraft:diamond"));

        assertEquals(1, count("gather", "d"));
    }

    @Test
    void theTrackerRecordsOnlyWhileAQuestTestsOrigins() {
        engine.register(quests.parse(NATURAL_ORE));
        assertNotNull(quests.provenance());
        assertTrue(quests.provenance().isRecording());

        engine.unregister(quests.id("ores"));

        assertFalse(quests.provenance().isRecording());
    }

    @Test
    void aTransientEngineRefusesAProvenanceTestNamingTheWayOut() {
        ECPluginData arena = Plugins.fakePluginData("ArenaQuests", tempDir.resolve("arena").toFile());
        try {
            QuestEngine transientEngine = QuestEngine.of(arena);
            QuestDefinition ores = QuestDefinitions.parseYaml(arena, NATURAL_ORE);
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> transientEngine.register(ores));
            assertTrue(refused.getMessage().contains("arenaquests:ores") && refused.getMessage().contains("ProvenanceTracker.open"),
                    refused.getMessage());
        } finally {
            QuestEngine.closeOwnedBy(arena);
            Plugins.forget("ArenaQuests");
        }
    }

    @Test
    void anUnknownProvenanceTestFailsNamingTheThree() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> quests.parse(
                "{id: x, stages: [{id: s, objectives: [{id: o, type: kill, provenance: {tamed: true}}]}]}"));
        assertTrue(refused.getMessage().contains("tamed") && refused.getMessage().contains("own-drop"), refused.getMessage());
    }
}
