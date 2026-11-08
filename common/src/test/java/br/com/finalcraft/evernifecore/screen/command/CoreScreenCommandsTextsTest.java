package br.com.finalcraft.evernifecore.screen.command;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.IconData;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenLayout;
import br.com.finalcraft.evernifecore.testing.Commands;
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

/** What {@code /ecore screen} says comes from the lang file: an edited text is what the admin reads, errors included. */
class CoreScreenCommandsTextsTest {

    @ScreenLayout(title = "Stall", rows = 1)
    public static class StallLayout extends Layout {

        @IconData(slot = {0})
        public Icon BUY = Icon.of(ItemSpec.of("minecraft:chest", 1));

    }

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand ecore;
    private TestCommandSender admin;

    @BeforeEach
    void open() {
        harness = Commands.harness("ScreenCmd", tempDir.resolve("cmd"));
        FCLocaleManager.loadLocale(harness.ecPluginData, CoreScreenCommands.class);
        ecore = harness.register(CoreCommand.class);
        admin = Senders.console("admin").grant(PermissionNodes.EVERNIFECORE_COMMAND_SCREEN);
        Layouts.clear();
    }

    @AfterEach
    void close() {
        Layouts.clear();
        harness.close();
    }

    private void assertReads(String key, String text, String line, String expected) {
        admin.clearMessages();
        try (Locales.Retext edited = Locales.retext(harness.ecPluginData, "CoreScreenCommands." + key, harness.ecPluginData.getPluginLanguage(), text)) {
            harness.dispatch(ecore, admin, line);
        }
        assertTrue(admin.getMessages().contains(expected), "expected <" + expected + "> in " + admin.getMessages());
    }

    @Test
    void reloadReadsItsEditedTextsForAllAndForOne() {
        Layouts.of(StallLayout.class);

        assertReads("RELOADED_ALL", "§b${layouts} back, ${redrawn} redrawn", "screen reload " + harness.pluginName, "§b1 back, 0 redrawn");
        assertReads("RELOADED_ONE", "§b${layout} back", "screen reload " + harness.pluginName + " StallLayout", "§bStallLayout back");
    }

    @Test
    void anUnknownPluginOrLayoutReadsItsEditedError() {
        assertReads("NO_PLUGIN", "§bWho is ${plugin}?", "screen reload Nobody", "§bWho is Nobody?");
        assertReads("NO_LAYOUT_NONE_LOADED", "§bNothing yet, ${layout}", "screen reload " + harness.pluginName + " Nope", "§bNothing yet, Nope");
        Layouts.of(StallLayout.class);
        assertReads("NO_LAYOUT", "§bNot ${layout}: ${loaded}", "screen reload " + harness.pluginName + " Nope", "§bNot Nope: StallLayout");
    }

    @Test
    void theLayoutInspectionReadsItsEditedHeaderVerdictAndEntry() {
        Layouts.of(StallLayout.class);
        String diff = "screen diff " + harness.pluginName + " StallLayout";

        assertReads("DIFF_HEADER", "§b${layout} in ${file}", diff, "§bStallLayout in guis/StallLayout.yml");
        assertReads("MATCHED", "§bSame: ${count}", diff, "§bSame: 1");
        assertReads("ENTRY_KEY", "§bKey ${key}", diff, "§bKey BUY   §7Slot [0]");
        assertReads("NO_OVERLAY", "§bNo ${language} yet", diff + " PT_BR", "§bNo PT_BR yet");
    }

    @Test
    void whichReadsItsEditedAnswerAndRefusal() {
        assertReads("NOTHING_ANSWERS", "§bNobody for ${key}", "screen which test:ghost", "§bNobody for test:ghost");
        assertReads("INVALID_KEY", "§bBad: ${reason}", "screen which not_a_key", "§bBad: " + invalidKeyReason("not_a_key"));
    }

    private static String invalidKeyReason(String text) {
        try {
            ScreenKey.parse(text);
            throw new AssertionError(text + " parses");
        } catch (IllegalArgumentException invalid) {
            return invalid.getMessage();
        }
    }
}
