package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.npc.Npcs;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.FakeNpcProvider;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which click on an NPC an {@code interact-npc} objective counts: the right one unless it says {@code click: left}. */
class QuestNpcClickTest {

    private static final NpcRef SMITH = NpcRef.of("citizens", "12");

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private FakeNpcProvider citizens;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        citizens = new FakeNpcProvider("citizens").at(SMITH, WorldLocPos.of(10, 64, 10, "world"));
        Npcs.register(quests.getOwner(), citizens);
        alex = quests.player("Alex");
    }

    @AfterEach
    void close() {
        Npcs.unregisterAll(quests.getOwner());
        quests.close();
        world.close();
    }

    private QuestState talk(String clickLine) {
        quests.getEngine().register(quests.parse("{id: talk, stages: [{id: a, objectives: [{id: o, type: interact-npc,"
                + " npc: 'citizens:12', goal: 9" + clickLine + "}]}]}"));
        StartResult result = await(quests.getEngine().start(quests.owner(alex), quests.id("talk"), QuestParams.NONE));
        assertTrue(result.isSuccess(), result.toString());
        assertTrue(citizens.isStarted(), "the active objective needs interact-npc");
        return state();
    }

    private QuestState state() {
        return quests.getEngine().getState(quests.owner(alex), quests.id("talk")).orElseThrow(() -> new AssertionError("no state"));
    }

    @Test
    void withoutClickTheRightClickCountsAndThePunchDoesNot() {
        talk("");

        citizens.leftClick(alex, SMITH);
        assertEquals(0, state().getCount("o"), "a punch is not a talk");

        citizens.click(alex, SMITH);
        assertEquals(1, state().getCount("o"));
    }

    @Test
    void clickLeftCountsThePunchAndNotTheRightClick() {
        talk(", click: left");

        citizens.click(alex, SMITH);
        assertEquals(0, state().getCount("o"));

        citizens.leftClick(alex, SMITH);
        assertEquals(1, state().getCount("o"));
    }

    @Test
    void anUnknownClickFailsTheLoadNamingWhatIsAccepted() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> quests.parse(
                "{id: talk, stages: [{id: a, objectives: [{id: o, type: interact-npc, npc: 'citizens:12', click: middle}]}]}"));

        String message = refused.getMessage();
        assertTrue(message.contains("right") && message.contains("left"), message);
    }
}
