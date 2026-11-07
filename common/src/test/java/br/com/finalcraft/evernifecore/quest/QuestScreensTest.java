package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.quest.screen.QuestJournalScreen;
import br.com.finalcraft.evernifecore.quest.screen.QuestOfferScreen;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void theJournalNamesTheStatusAndTheFilterInTheViewersLanguage() {
        ECPluginData core = screens.getCore();
        String before = core.getPluginLanguage();
        Plugins.setLanguage(core, LocaleType.PT_BR);
        try {
            assertTrue(await(engine.start(quests.owner(alex), quests.id("walk"), QuestParams.NONE)).isSuccess());
            ScreenView view = open(ScreenKey.QUEST_JOURNAL, QuestJournalScreen.Request.of(engine, QuestStatus.ACTIVE));
            RecordingSurface surface = screens.getRenderer().surfaceOf(view);

            assertTrue(surface.getSlot(0).getItem().getLore().contains("§fAtiva §7- §a0%"), surface.getSlot(0).getItem().getLore().toString());
            assertTrue(surface.getSlot(49).getItem().getDisplayName().endsWith("§fAtiva"), surface.getSlot(49).getItem().getDisplayName());

            screens.click(view, "filter", ClickGesture.PRIMARY);
            screens.advanceTicks(2);
            assertTrue(surface.getSlot(49).getItem().getDisplayName().endsWith("§fDisponível"), surface.getSlot(49).getItem().getDisplayName());
            view.close();
            screens.advanceTicks(2);

            ScreenView every = open(ScreenKey.QUEST_JOURNAL, QuestJournalScreen.Request.of(engine, null));
            RecordingSurface everySurface = screens.getRenderer().surfaceOf(every);
            assertTrue(everySurface.getSlot(49).getItem().getDisplayName().endsWith("§fTodas"), everySurface.getSlot(49).getItem().getDisplayName());
            every.close();
            screens.advanceTicks(2);
        } finally {
            Plugins.setLanguage(core, before);
        }
    }

    @Test
    void theJournalHidesWhatVisibilityHides() {
        engine.register(quests.parse("{id: vault, meta: {visibility: secret}, gate: {require: [{type: permission, node: fq.vault}]},"
                + " stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        engine.register(quests.parse("{id: cellar, meta: {visibility: hidden}, stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));

        ScreenView before = open(ScreenKey.QUEST_JOURNAL, QuestJournalScreen.Request.of(engine, null));
        List<QuestId> listed = ((QuestJournalScreen) before.getScreen()).getListed();
        assertTrue(listed.contains(quests.id("vip")) && listed.contains(quests.id("walk")), "a visible quest lists even locked: " + listed);
        assertFalse(listed.contains(quests.id("vault")) || listed.contains(quests.id("cellar")), listed.toString());
        before.close();
        screens.advanceTicks(2);

        alex.grant("fq.vault");
        assertTrue(await(engine.start(quests.owner(alex), quests.id("cellar"), QuestParams.NONE)).isSuccess());
        ScreenView after = open(ScreenKey.QUEST_JOURNAL, QuestJournalScreen.Request.of(engine, null));
        listed = ((QuestJournalScreen) after.getScreen()).getListed();
        assertTrue(listed.contains(quests.id("vault")) && listed.contains(quests.id("cellar")), listed.toString());
        after.close();
        screens.advanceTicks(2);
    }

    @Test
    void bothScreensDrawAQuestWithTheIconOfItsMeta() {
        engine.register(quests.parse("{id: ore, name: Ore, meta: {icon: 'minecraft:iron_ore'}, stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));

        ScreenView journal = open(ScreenKey.QUEST_JOURNAL, QuestJournalScreen.Request.of(engine, null));
        List<String> materials = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            materials.add(screens.getRenderer().surfaceOf(journal).getSlot(slot).getItem().getMaterialId());
        }
        assertEquals(Arrays.asList("minecraft:book", "minecraft:book", "minecraft:iron_ore"), materials, "a quest without one keeps the layout's");
        assertEquals("§eOre", screens.getRenderer().surfaceOf(journal).getSlot(2).getItem().getDisplayName(), "the layout's text stays");
        journal.close();
        screens.advanceTicks(2);

        ScreenView offer = open(ScreenKey.QUEST_OFFER, QuestOfferScreen.Request.of(engine, quests.id("ore")));
        assertEquals("minecraft:iron_ore", screens.getRenderer().surfaceOf(offer).getSlot(4).getItem().getMaterialId());
        offer.close();
        screens.advanceTicks(2);
    }

    @Test
    void theOfferGreysWhatTheGateMissesAndAcceptStartsTheQuest() {
        ScreenView vip = open(ScreenKey.QUEST_OFFER, QuestOfferScreen.Request.of(engine, quests.id("vip")));
        String missing = ((QuestOfferScreen) vip.getScreen()).getMissing();
        assertTrue(missing.contains("fq.vip"), missing);
        assertTrue(screens.getRenderer().surfaceOf(vip).getSlot(11).getItem().getLore().contains("§8" + missing),
                "the grey is the layout's: " + screens.getRenderer().surfaceOf(vip).getSlot(11).getItem().getLore());
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
