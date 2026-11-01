package br.com.finalcraft.evernifecore.hytale.quest;

import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the hytale-native bridge reads and refuses, through the check every engine runs on registration - no server. */
class HyNativeObjectiveSpecTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private AutoCloseable check;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        check = QuestEngine.addDefinitionCheck(HyNativeObjectiveSpec::require);
    }

    @AfterEach
    void close() throws Exception {
        check.close();
        quests.close();
        world.close();
    }

    @Test
    void aMarkersQuestWithAChatObjectiveFailsOnRegisterNamingTheObjectiveAndWhatMaps() {
        QuestDefinition definition = quests.parse("{id: gossip, hytale-native: {markers: true}, stages: [{id: a, objectives:"
                + " [{id: ore, type: break-block}, {id: talk, type: chat}]}]}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> quests.getEngine().register(definition));

        String text = refused.getMessage();
        assertTrue(text.contains("'talk'") && text.contains("'chat'") && text.contains(String.join(", ", HyNativeObjectiveSpec.MAPPED)), text);
        assertNull(quests.getEngine().get(definition.getId()), "a refused quest is not registered");
    }

    @Test
    void aQuestOfMappedObjectivesRegistersAndReadsItsSwitches() {
        QuestDefinition definition = quests.parse("{id: mine, hytale-native: {markers: true, npc-actions: true, asset: 'FinalQuests:Mine_Iron'},"
                + " stages: [{id: a, objectives: [{id: ore, type: break-block}, {id: wolf, type: kill}]}]}");

        quests.getEngine().register(definition);

        HyNativeObjectiveSpec spec = HyNativeObjectiveSpec.of(definition);
        assertNotNull(spec);
        assertTrue(spec.isMarkers() && spec.isNpcActions());
        assertFalse(spec.isSpawns());
        assertEquals("FinalQuests:Mine_Iron", spec.getAsset());
        assertNotNull(quests.getEngine().get(definition.getId()));
    }

    @Test
    void npcActionsWithoutAnAssetAndAnUnknownKeyAreRefused() {
        QuestDefinition noAsset = quests.parse("{id: npc, hytale-native: {npc-actions: true}, stages: [{id: a, objectives: [{id: o, type: kill}]}]}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> quests.getEngine().register(noAsset)).getMessage().contains("asset:"));

        QuestDefinition typo = quests.parse("{id: typo, hytale-native: {marker: true}, stages: [{id: a, objectives: [{id: o, type: kill}]}]}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> quests.getEngine().register(typo)).getMessage().contains("'marker'"));
    }

    @Test
    void withEverySwitchOffOrNoMapAnyObjectiveRegisters() {
        QuestDefinition off = quests.parse("{id: off, hytale-native: {markers: false}, stages: [{id: a, objectives: [{id: talk, type: chat}]}]}");
        QuestDefinition plain = quests.parse("{id: plain, stages: [{id: a, objectives: [{id: talk, type: chat}]}]}");

        quests.getEngine().register(off);
        quests.getEngine().register(plain);

        assertNull(HyNativeObjectiveSpec.of(off));
        assertNull(HyNativeObjectiveSpec.of(plain));
        assertEquals(2, quests.getEngine().getAll().size());
    }
}
