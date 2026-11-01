package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.quest.screen.QuestJournalScreen;
import br.com.finalcraft.evernifecore.quest.screen.QuestOfferScreen;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's default quest screens: the journal lists by status, the offer greys what the gate misses and accept starts. */
class QuestScreensTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld screens;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        screens = ScreenWorld.open(tempDir.resolve("screens"), platform -> platform.platformProviderId("test"));
        alex = screens.viewer("Alex");
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        quests.login(alex);
        engine = quests.getEngine();
        engine.register(quests.parse("{id: walk, name: Walk, stages: [{id: s, objectives: [{id: dirt, type: break-block, goal: 2}]}]}"));
        engine.register(quests.parse("{id: vip, name: Vip, gate: {require: [{type: permission, node: fq.vip}]},"
                + " stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        Layouts.clear();
    }

    @AfterEach
    void close() {
        Layouts.clear();
        quests.close();
        screens.close();
    }

    private ScreenView open(ScreenKey key, Object request) {
        ScreenView view = Screens.open(alex, key, ScreenRequest.of(quests.getOwner()).payload(request)).join();
        screens.advanceTicks(2);
        return view;
    }

    @Test
    void theJournalListsByStatusAndTheFilterCycles() {
        alex.grant("fq.vip");
        assertTrue(await(engine.start(quests.owner(alex), quests.id("walk"), QuestParams.NONE)).isSuccess());

        ScreenView view = open(ScreenKey.QUEST_JOURNAL, QuestJournalScreen.Request.of(engine, QuestStatus.ACTIVE));
        QuestJournalScreen journal = (QuestJournalScreen) view.getScreen();
        assertEquals(Collections.singletonList(quests.id("walk")), journal.getListed());

        screens.click(view, "filter", ClickGesture.PRIMARY);
        screens.advanceTicks(2);

        assertEquals(QuestStatus.AVAILABLE, journal.getFilter());
        assertEquals(Collections.singletonList(quests.id("vip")), journal.getListed(), "walk is active, vip is the available one");
        view.close();
        screens.advanceTicks(2);
    }

    @Test
    void theOfferGreysWhatTheGateMissesAndAcceptStartsTheQuest() {
        ScreenView vip = open(ScreenKey.QUEST_OFFER, QuestOfferScreen.Request.of(engine, quests.id("vip")));
        assertTrue(((QuestOfferScreen) vip.getScreen()).getMissing().contains("fq.vip"), ((QuestOfferScreen) vip.getScreen()).getMissing());
        vip.close();
        screens.advanceTicks(2);

        ScreenView walk = open(ScreenKey.QUEST_OFFER, QuestOfferScreen.Request.of(engine, quests.id("walk")));
        assertEquals("", ((QuestOfferScreen) walk.getScreen()).getMissing());
        screens.click(walk, "accept", ClickGesture.PRIMARY);
        screens.advanceTicks(2);

        assertEquals(QuestStatus.ACTIVE, engine.getStatus(quests.owner(alex), quests.id("walk")));
        assertEquals(QuestStatus.LOCKED, engine.getStatus(quests.owner(alex), quests.id("vip")), "its gate still fails");
    }
}
