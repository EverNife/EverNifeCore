package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.dialogue.command.CoreDialogueCommands;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.DialogueTrees;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@code /ecore dialogue <player>} says comes from the lang file: an edited text is what the admin reads. */
class CoreDialogueCommandsTextsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private DialogueFixture world;
    private FinalCMDPluginCommand ecore;
    private TestCommandSender admin;

    @BeforeEach
    void open() {
        harness = Commands.harness("DialogueCmd", tempDir.resolve("cmd"));
        FCLocaleManager.loadLocale(harness.ecPluginData, CoreDialogueCommands.class);
        ecore = harness.register(CoreCommand.class);
        world = DialogueFixture.open(tempDir.resolve("dialogue"));
        admin = Senders.console("admin").grant(PermissionNodes.EVERNIFECORE_COMMAND_DIALOGUE);
    }

    @AfterEach
    void close() {
        world.close();
        harness.close();
    }

    private void assertReads(String key, String text, String expected) {
        admin.clearMessages();
        try (Locales.Retext edited = Locales.retext(harness.ecPluginData, "CoreDialogueCommands." + key, harness.ecPluginData.getPluginLanguage(), text)) {
            harness.dispatch(ecore, admin, "dialogue Alex");
        }
        assertTrue(admin.getMessages().contains(expected), "expected <" + expected + "> in " + admin.getMessages());
    }

    @Test
    void aPlayerInNoDialogueReadsItsEditedError() {
        assertReads("NO_DIALOGUE", "§b${player} talks to nobody", "§bAlex talks to nobody");
    }

    @Test
    void everyLineOfTheStateOfAPlayerReadsItsEditedText() {
        world.start(world.register(DialogueTrees.linear(world.owner(), "tomas", "Hello.", "Bye.")));
        world.screens.advanceTicks(2);

        assertReads("HEADER", "§b${player} in ${dialogue}", "§bAlex in fakecolonies:tomas");
        assertReads("NODE_LINE", "§bat ${node}, ${seconds}s", "§bat " + Dialogues.sessionOf(world.alex).getNode().getId() + ", 0s");
        assertReads("STARTED_BY", "§bby ${caller}", "§bby fakecolonies:test");
    }
}
