package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.command.CoreGroupCommands;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.quest.command.ArgParserQuest;
import br.com.finalcraft.evernifecore.quest.command.QuestCommandNode;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest verbs a plugin mounts ({@link QuestCommandNode}) and the core's read-only {@code /ecore quest <player>}. */
class QuestCommandsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private QuestWorld quests;
    private QuestEngine engine;
    private FinalCMDPluginCommand fq;
    private TestCommandSender admin;
    private TestFPlayerSender alex;

    /** {@code /fq quest ...}, as a quest plugin mounts the node. */
    @FinalCMD(aliases = "fq")
    public static class Fq_Cmd {
        @FinalCMD.Node(subcmd = "quest", permission = "fq.quest")
        final QuestCommandNode quests;

        Fq_Cmd(QuestCommandNode quests) {
            this.quests = quests;
        }
    }

    @BeforeEach
    void open() {
        harness = Commands.harness("QuestCmd", tempDir.resolve("cmd"));
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        FCLocaleManager.loadLocale(harness.ecPluginData, QuestCommandNode.class, ArgParserQuest.class, CoreGroupCommands.class);
        engine.register(quests.parse("{id: walk, name: Walk, stages: [{id: s, objectives: [{id: dirt, type: break-block,"
                + " block: \"minecraft:dirt\", goal: 3}]}]}"));
        engine.register(quests.parse("{id: talk, name: Talk, stages: [{id: s, objectives: [{id: c, type: kill}]}]}"));
        fq = harness.register(new Fq_Cmd(new QuestCommandNode(engine, "fq.quest")));
        admin = Senders.console("admin").grant("fq.quest");
        for (String verb : new String[]{"list", "info", "start", "offer", "abandon", "reset", "complete", "progress", "track"}) {
            admin.grant("fq.quest." + verb);
        }
        alex = quests.player("Alex").grant("fq.quest");
    }

    @AfterEach
    void close() {
        quests.close();
        harness.close();
    }

    private QuestStatus status(String quest) {
        return engine.getStatus(quests.owner(alex), quests.id(quest));
    }

    private void fq(Object sender, String line) {
        harness.dispatch(fq, (FCommandSender) sender, line);
    }

    @Test
    void eachVerbActsOnTheNodesEngine() {
        alex.grant("fq.quest.info").grant("fq.quest.start").grant("fq.quest.abandon").grant("fq.quest.track");

        fq(admin, "quest start walk Alex");
        assertEquals(QuestStatus.ACTIVE, status("walk"));
        fq(admin, "quest progress walk dirt 2 Alex");
        assertEquals(2, engine.getState(quests.owner(alex), quests.id("walk")).get().getCount("dirt"));
        fq(alex, "quest info walk");
        alex.assertAnyMessageContains("2/3");
        fq(admin, "quest list ACTIVE Alex");
        assertTrue(admin.getMessages().stream().anyMatch(line -> line.contains("walk") && line.contains("ACTIVE")), admin.getMessages().toString());
        assertFalse(admin.getMessages().stream().anyMatch(line -> line.contains("talk")), "the filter leaves the available quest out");
        fq(admin, "quest complete walk Alex");
        assertEquals(QuestStatus.COMPLETED, status("walk"));

        fq(admin, "quest offer talk Alex");
        assertEquals(QuestStatus.OFFERED, status("talk"));
        fq(alex, "quest start talk");
        assertEquals(QuestStatus.ACTIVE, status("talk"), alex.getMessages().toString());
        fq(alex, "quest track talk");
        assertEquals(quests.id("talk"), engine.getTracked(alex), alex.getMessages().toString());
        fq(alex, "quest track off");
        assertNull(engine.getTracked(alex));
        fq(alex, "quest abandon talk");
        assertEquals(QuestStatus.ABANDONED, status("talk"));
    }

    @Test
    void listHidesASecretQuestWhileLockedAndAHiddenOneUntilStarted() {
        alex.grant("fq.quest.list");
        engine.register(quests.parse("{id: vault, meta: {visibility: secret}, gate: {require: [{type: permission, node: fq.vault}]},"
                + " stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        engine.register(quests.parse("{id: cellar, meta: {visibility: hidden}, stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));

        fq(alex, "quest list");
        assertTrue(alex.getMessages().stream().anyMatch(line -> line.contains("walk")), alex.getMessages().toString());
        assertFalse(alex.getMessages().stream().anyMatch(line -> line.contains("vault") || line.contains("cellar")), alex.getMessages().toString());

        alex.grant("fq.vault");
        fq(admin, "quest start cellar Alex");
        alex.clearMessages();
        fq(alex, "quest list");
        assertTrue(alex.getMessages().stream().anyMatch(line -> line.contains("vault")), "available now, so listed: " + alex.getMessages());
        assertTrue(alex.getMessages().stream().anyMatch(line -> line.contains("cellar")), "started, so listed: " + alex.getMessages());
    }

    @Test
    void aPresenterRewritesTheNoGroupRefusalOfStartAndOffer() {
        GroupWorld parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        try {
            parties.kind("party", GroupKind.Cardinality.ONE);
            engine.register(quests.parse("{id: raid, owner: {type: group}, stages: [{id: s, objectives: [{id: z, type: kill}]}]}"));
            engine.setPresenter(new QuestPresenter() {
                @Override
                public FancyText refusal(FPlayer player, QuestDefinition definition, StartResult result) {
                    return result.getReason() == StartResult.Reason.NO_GROUP ? FancyText.of("Join a party, " + player.getName()) : null;
                }
            });

            fq(admin, "quest start raid Alex");
            fq(admin, "quest offer raid Alex");

            assertEquals(2, admin.getMessages().stream().filter(line -> line.contains("Join a party, Alex")).count(), admin.getMessages().toString());
            assertFalse(admin.getMessages().stream().anyMatch(line -> line.contains("no such group")), admin.getMessages().toString());
        } finally {
            parties.close();
        }
    }

    @Test
    void aVerbNeedsItsOwnPermissionBeyondTheMount() {
        alex.grant("fq.quest.list");
        fq(admin, "quest start walk Alex");

        fq(alex, "quest complete walk Alex");
        assertEquals(QuestStatus.ACTIVE, status("walk"), "without fq.quest.complete the admin verb did not run");

        fq(alex, "quest list");
        alex.assertAnyMessageContains("walk");
    }

    @Test
    void resetDropsTheStateOfAQuestNoLongerRegistered() {
        fq(admin, "quest start walk Alex");
        engine.unregister(quests.id("walk"));
        assertTrue(engine.getOrphans(quests.owner(alex)).contains("fakequests:walk"));

        fq(admin, "quest reset walk Alex");

        assertTrue(engine.getOrphans(quests.owner(alex)).isEmpty(), admin.getMessages().toString());
    }

    @Test
    void anUnknownQuestIsRefusedNamingIt() {
        fq(admin, "quest start nope Alex");
        assertTrue(admin.getMessages().stream().anyMatch(line -> line.contains("nope")), admin.getMessages().toString());
    }

    @Test
    void ecoreQuestShowsTheActiveQuestAndTheSourceItListensTo() {
        FinalCMDPluginCommand ecore = harness.register(CoreCommand.class);
        admin.grant(PermissionNodes.EVERNIFECORE_COMMAND_QUEST);
        fq(admin, "quest start walk Alex");

        harness.dispatch(ecore, admin, "quest Alex");

        assertTrue(admin.getMessages().stream().anyMatch(line -> line.contains("fakequests:walk") && line.contains("dirt=0")), admin.getMessages().toString());
        assertTrue(admin.getMessages().stream().anyMatch(line -> line.contains(EventSource.BREAK.getName()) && line.contains("fakequests:walk/dirt")),
                admin.getMessages().toString());
    }
}
