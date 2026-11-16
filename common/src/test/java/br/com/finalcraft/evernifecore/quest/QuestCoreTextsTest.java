package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.command.CoreQuestCommands;
import br.com.finalcraft.evernifecore.quest.command.QuestCommandNode;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.RecordingNotifier;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the core says about quests comes from its lang files: the default presenter in the language of the plugin that
 * owns the engine, {@code /ecore quest} in the core's.
 */
class QuestCoreTextsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private ECPluginData core;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;
    private RecordingNotifier recorder;

    @BeforeEach
    void open() {
        harness = Commands.harness("QuestTexts", tempDir.resolve("cmd"));
        core = harness.withSeparateCore(tempDir.resolve("core"));
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        Plugins.setLanguage(core, LocaleType.EN_US);
        Plugins.setLanguage(quests.getOwner(), LocaleType.PT_BR);
        //the core's boot owns these texts (CORE_LOCALE_CLASSES), loaded last, as on a server
        FCLocaleManager.loadLocale(core, NotifierQuestPresenter.class, CoreQuestCommands.class, QuestCommandNode.class);
        engine.register(quests.parse("{id: walk, name: Walk, stages: [{id: s, objectives: [{id: dirt, type: break-block,"
                + " block: \"minecraft:dirt\", goal: 3}]}]}"));
        alex = quests.player("Alex");
        QuestWorld.await(engine.start(quests.owner(alex), quests.id("walk"), QuestParams.of(Collections.emptyMap())));
        recorder = RecordingNotifier.install(quests.getOwner());
    }

    @AfterEach
    void close() {
        recorder.uninstall(quests.getOwner());
        quests.close();
        harness.close();
    }

    private QuestState walk() {
        return engine.getState(quests.owner(alex), quests.id("walk")).get();
    }

    private QuestDefinition walkDefinition() {
        return engine.get(quests.id("walk"));
    }

    @Test
    void aCompletedQuestReadsTheCoresTextInTheLanguageOfThePluginThatOwnsTheEngine() {
        try (Locales.Retext ignored = Locales.retext(core, "NotifierQuestPresenter.COMPLETED", "PT_BR", "§aConcluída: ${quest}")) {
            new NotifierQuestPresenter().onQuestComplete(alex, engine, walkDefinition(), walk());
        }

        assertTrue(recorder.to(alex).stream().anyMatch(delivery -> delivery.getText().equals("Concluída: Walk")),
                recorder.getDeliveries().toString());
    }

    @Test
    void anEditedLangLineChangesTheProgressText() {
        try (Locales.Retext ignored = Locales.retext(core, "NotifierQuestPresenter.PROGRESS", "PT_BR", "§b${quest} -> ${count}/${goal}")) {
            new NotifierQuestPresenter().onProgress(alex, engine, walkDefinition(), walk(), walkDefinition().getObjectives().get(0));
        }

        assertTrue(recorder.to(alex).stream().anyMatch(delivery -> delivery.getText().equals("Walk -> 0/3")),
                recorder.getDeliveries().toString());
    }

    @Test
    void ecoreQuestPrintsTheSameLinesAsBeforeInEnglishAndTranslatedInPortuguese() {
        TestCommandSender admin = Senders.console("admin");
        CoreQuestCommands.show(admin, alex);
        assertEquals(linesAsWrittenBeforeTheLocale(alex), admin.getMessages());

        Plugins.setLanguage(core, LocaleType.PT_BR);
        TestCommandSender portuguese = Senders.console("admin");
        CoreQuestCommands.show(portuguese, alex);
        assertEquals("§6Quests de §eAlex§6:", portuguese.getMessages().get(0));
        assertTrue(portuguese.getMessages().stream().anyMatch(line -> line.contains(" estágio §fs")), portuguese.getMessages().toString());
    }

    /** What {@code /ecore quest <player>} printed when its lines were written in the code, for the comparison. */
    private static List<String> linesAsWrittenBeforeTheLocale(TestFPlayerSender target) {
        List<String> lines = new ArrayList<>();
        ProgressOwner player = ProgressOwner.player(target.getUniqueId());
        lines.add("§6Quests of §e" + target.getName() + "§6:");
        for (QuestEngine engine : QuestEngine.all()) {
            lines.add("§6 " + engine.getOwner().getMetaInfo().getName() + "§7 (" + engine.getAll().size() + " quest(s))");
            for (QuestState state : engine.getStates(player)) {
                if (state.getStatus() == QuestStatus.ACTIVE) {
                    QuestDefinition definition = engine.get(QuestId.parse(state.getQuestId(), engine.getOwner().getMetaInfo().getName()));
                    Stage stage = definition == null ? null : definition.getStage(state.getStageId());
                    StringBuilder counts = new StringBuilder();
                    for (Objective objective : stage == null ? Collections.<Objective>emptyList() : stage.getObjectives().getObjectives()) {
                        counts.append(' ').append(objective.getId()).append('=').append(state.getCount(objective.getId()));
                    }
                    lines.add("§a   " + state.getQuestId() + "§7 stage §f" + state.getStageId() + "§7" + counts);
                }
            }
            Collection<String> orphans = engine.getOrphans(player);
            if (!orphans.isEmpty()) {
                lines.add("§7   orphan states (quest not registered; reset drops them): §f" + String.join(", ", orphans));
            }
        }
        List<EventSource> listened = QuestIndex.get().getListenedSources();
        lines.add("§6Sources listened to: §e" + listened.size());
        for (EventSource source : listened) {
            List<String> mine = new ArrayList<>();
            for (QuestIndex.Entry entry : QuestIndex.get().getEntries(source, player)) {
                mine.add(entry.getQuestId() + "/" + entry.getObjective().getId());
            }
            lines.add("§7 - §f" + source.getName() + (mine.isEmpty() ? "" : "§7 for " + String.join(", ", mine)));
        }
        return lines;
    }
}
