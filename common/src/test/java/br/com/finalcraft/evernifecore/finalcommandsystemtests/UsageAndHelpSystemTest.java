package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins usage/help/hover/locale rendering (matrix D): {@code CMDMethodInterpreter.buildHelpLine}
 * and {@code HelpContext.sendTo}.
 */
class UsageAndHelpSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("UsageHelp", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // D1 - a subcommand with no @Arg and a usage() uses the usage text (with legacy %name%/%label%
    // stripped) for the help line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d1cmd")
    public static class D1_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub", usage = "%name% does the thing %label%")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void d1_noArgSubCommandWithUsageUsesTheUsageTextStrippedOfLegacyPlaceholders() {
        FinalCMDPluginCommand command = newHarness().register(new D1_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("does the thing");
        //%name% and %label% inside usage() itself are legacy placeholders that get stripped to "",
        //not resolved - unlike the framework's OWN %label%/%subcmd% around it
        assertFalse(sender.anyMessageContains("%name%"));
    }

    // ------------------------------------------------------------------
    // D2 - a subcommand WITH @Arg ignores usage() entirely; the line is built from the @Arg names
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d2cmd")
    public static class D2_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub", usage = "THIS_USAGE_MUST_NOT_APPEAR")
        public void sub(FCommandSender sender, @Arg(name = "<value>") String value) {}
    }

    @Test
    void d2_subCommandWithArgIgnoresUsageAndBuildsFromArgNames() {
        FinalCMDPluginCommand command = newHarness().register(new D2_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("<value>");
        assertFalse(sender.anyMessageContains("THIS_USAGE_MUST_NOT_APPEAR"));
    }

    // ------------------------------------------------------------------
    // D3 - a subcommand with no locales() and no descriptionOverride has NO hover on its help line
    // (locales() is the only declarative path left; see CustomizeSystemTest#g4 for the
    // runtime-only setDescriptionOverride() path)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d3cmd")
    public static class D3_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void d3_noLocalesAndNoDescriptionOverrideMeansNoHoverOnTheHelpLine() {
        FinalCMDPluginCommand command = newHarness().register(new D3_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("d3cmd sub");
        assertNull(sender.hoverTextOfMessageContaining("d3cmd sub"));
    }

    // ------------------------------------------------------------------
    // D4 - locales() on @SubCMD makes the hover come from the FCLocale (EN_US), keyed by
    // DeclaringClass.METODO (reloadable through FCLocaleScanner)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d4cmd")
    public static class D4_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub", locales = {@FCLocale(lang = LocaleType.EN_US, text = "From an FCLocale")})
        public void sub(FCommandSender sender) {}
    }

    @Test
    void d4_localesOnSubCommandDrivesTheHoverFromFCLocale() {
        FinalCMDPluginCommand command = newHarness().register(new D4_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("d4cmd sub");
        assertNotNull(hover);
        assertTrue(hover.contains("From an FCLocale"));
    }

    // ------------------------------------------------------------------
    // D5 - @Arg(locales=...) adds an extra block to the hover (the "✯ [<arg>]" pattern)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d5cmd")
    public static class D5_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                         @Arg(name = "<value>", locales = {@FCLocale(lang = LocaleType.EN_US, text = "The value to use")}) String value) {}
    }

    @Test
    void d5_argLocalesAddAnExtraHoverBlock() {
        FinalCMDPluginCommand command = newHarness().register(new D5_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("d5cmd sub");
        assertNotNull(hover);
        assertTrue(hover.contains("✯"));
        assertTrue(hover.contains("value"));
        assertTrue(hover.contains("The value to use"));
    }

    // ------------------------------------------------------------------
    // D6 - HelpContext.sendTo filters lines by sub-command permission; a sender with no permission
    // at all gets needsThePermission instead; a custom header is respected
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d6cmd", helpHeader = "MY CUSTOM HEADER")
    public static class D6_Cmd {
        @FinalCMD.SubCMD(subcmd = "open")
        public void open(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "restricted", permission = "d6.restricted")
        public void restricted(FCommandSender sender) {}
    }

    @Test
    void d6_helpFiltersRestrictedLinesAndRespectsTheCustomHeader() {
        FinalCMDPluginCommand command = newHarness().register(new D6_Cmd());
        TestCommandSender sender = new TestCommandSender("console"); //no permissions granted

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("d6cmd open");
        assertFalse(sender.anyMessageContains("d6cmd restricted"), "the restricted line should have been filtered out");
        sender.assertAnyMessageContains("MY CUSTOM HEADER");
    }

    @Test
    void d6_senderWithNoPermissionAtAllGetsNeedsThePermissionInstead() {
        newHarness();
        TestCommandSender sender = new TestCommandSender("console"); //no permissions granted

        //Every subcommand of this command is permission-gated, so the sender qualifies for zero lines
        FinalCMDPluginCommand allRestricted = harness.register(new D6_AllRestrictedCmd());
        harness.dispatch(allRestricted, sender, "help");

        sender.assertAnyMessageContains("permission");
        assertFalse(sender.anyMessageContains("MY CUSTOM HEADER"), "the header must not be sent when every line was filtered out");
    }

    @FinalCMD(aliases = "d6restrictedcmd", helpHeader = "MY CUSTOM HEADER")
    public static class D6_AllRestrictedCmd {
        @FinalCMD.SubCMD(subcmd = "one", permission = "d6.one")
        public void one(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "two", permission = "d6.two")
        public void two(FCommandSender sender) {}
    }

    // ------------------------------------------------------------------
    // D7 - a class's own @FCLocale static LocaleMessage fields are loaded at registration, and
    // ${label} is resolved at dispatch time (prepareClassLocales)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "d7cmd")
    public static class D7_Cmd {
        @FCLocale(lang = LocaleType.EN_US, text = "You used ${label}!")
        static LocaleMessage GREETING;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            GREETING.send(sender);
        }
    }

    @Test
    void d7_classOwnFCLocaleFieldsAreLoadedAndLabelIsResolvedAtDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new D7_Cmd());
        assertNotNull(D7_Cmd.GREETING, "the field should have been populated at registration time");

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "sub");

        sender.assertAnyMessageContains("You used d7cmd!");
    }
}
