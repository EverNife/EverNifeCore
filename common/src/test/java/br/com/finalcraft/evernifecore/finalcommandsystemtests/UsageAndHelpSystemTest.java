package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpWords;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins usage/help/hover/locale rendering: {@code CMDMethodInterpreter.buildHelpLine}
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
    // a subcommand with no @Arg and a usage() puts the usage text on the help line, exactly as
    // written: nothing inside it is a placeholder and nothing is stripped out of it
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "verbatimusage")
    public static class VerbatimUsageCmd {
        @FinalCMD.SubCMD(subcmd = "sub", usage = "%name% does the thing %label%")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void noArgSubCommandWithUsageRendersTheUsageTextVerbatim() {
        FinalCMDPluginCommand command = newHarness().register(new VerbatimUsageCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("verbatimusage sub %name% does the thing %label%");
    }

    // ------------------------------------------------------------------
    // a subcommand WITH @Arg builds its line from the @Arg names, and a usage() written next to
    // them is dead text: declaring both is refused at registration
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "arghelpline")
    public static class ArgBuiltHelpLineCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") String value) {}
    }

    @FinalCMD(aliases = "usagewitharg")
    public static class UsageBesideArgCmd {
        @FinalCMD.SubCMD(subcmd = "sub", usage = "THIS_USAGE_MUST_NOT_APPEAR")
        public void sub(FCommandSender sender, @Arg("<value>") String value) {}
    }

    @Test
    void subCommandWithArgBuildsTheLineFromTheArgNames() {
        FinalCMDPluginCommand command = newHarness().register(new ArgBuiltHelpLineCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("<value>");
    }

    @Test
    void usageDeclaredNextToAnArgIsRefusedAtRegistration() {
        newHarness();

        ArgMountException error = harness.registerExpectingError(new UsageBesideArgCmd());

        assertTrue(error.getMessage().contains("THIS_USAGE_MUST_NOT_APPEAR"), error.getMessage());
        assertTrue(error.getMessage().contains("sub"), error.getMessage());
        assertTrue(error.getMessage().contains("Delete the usage()"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // a subcommand with no locales() and no descriptionOverride has NO hover on its help line
    // (locales() is the only declarative path left; CustomizeSystemTest covers the runtime-only
    // setDescriptionOverride() path)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "nohover")
    public static class NoHoverCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void noLocalesAndNoDescriptionOverrideMeansNoHoverOnTheHelpLine() {
        FinalCMDPluginCommand command = newHarness().register(new NoHoverCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("nohover sub");
        assertNull(sender.hoverTextOfMessageContaining("nohover sub"));
    }

    // ------------------------------------------------------------------
    // locales() on @SubCMD makes the hover come from the FCLocale (EN_US), keyed by
    // DeclaringClass.METODO (reloadable through FCLocaleScanner)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "localeshover")
    public static class LocalesHoverCmd {
        @FinalCMD.SubCMD(subcmd = "sub", locales = {@FCLocale(lang = LocaleType.EN_US, text = "From an FCLocale")})
        public void sub(FCommandSender sender) {}
    }

    @Test
    void localesOnSubCommandDrivesTheHoverFromFCLocale() {
        FinalCMDPluginCommand command = newHarness().register(new LocalesHoverCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("localeshover sub");
        assertNotNull(hover);
        assertTrue(hover.contains("From an FCLocale"));
    }

    // ------------------------------------------------------------------
    // @Arg(locales=...) adds an extra block to the hover (the "✯ [<arg>]" pattern)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "arglocaleshover")
    public static class ArgLocalesHoverCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                         @Arg(value = "<value>", locales = {@FCLocale(lang = LocaleType.EN_US, text = "The value to use")}) String value) {}
    }

    @Test
    void argLocalesAddAnExtraHoverBlock() {
        FinalCMDPluginCommand command = newHarness().register(new ArgLocalesHoverCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("arglocaleshover sub");
        assertNotNull(hover);
        assertTrue(hover.contains("✯"));
        assertTrue(hover.contains("value"));
        assertTrue(hover.contains("The value to use"));
        //this subcommand has no description of its own, and an absent one renders as nothing, not as "null"
        assertFalse(hover.contains("null"), hover);
    }

    // ------------------------------------------------------------------
    // HelpContext.sendTo filters lines by sub-command permission; a sender with no permission
    // at all gets needsThePermission instead; a custom header is respected
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "restrictedhelp", helpHeader = "MY CUSTOM HEADER")
    public static class RestrictedHelpLinesCmd {
        @FinalCMD.SubCMD(subcmd = "open")
        public void open(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "restricted", permission = "restrictedhelp.restricted")
        public void restricted(FCommandSender sender) {}
    }

    @Test
    void helpFiltersRestrictedLinesAndRespectsTheCustomHeader() {
        FinalCMDPluginCommand command = newHarness().register(new RestrictedHelpLinesCmd());
        TestCommandSender sender = new TestCommandSender("console"); //no permissions granted

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("restrictedhelp open");
        assertFalse(sender.anyMessageContains("restrictedhelp restricted"), "the restricted line should have been filtered out");
        sender.assertAnyMessageContains("MY CUSTOM HEADER");
    }

    @Test
    void senderWithNoPermissionAtAllGetsNeedsThePermissionInstead() {
        newHarness();
        TestCommandSender sender = new TestCommandSender("console"); //no permissions granted

        //Every subcommand of this command is permission-gated, so the sender qualifies for zero lines
        FinalCMDPluginCommand allRestricted = harness.register(new AllRestrictedHelpCmd());
        harness.dispatch(allRestricted, sender, "help");

        sender.assertAnyMessageContains("permission");
        assertFalse(sender.anyMessageContains("MY CUSTOM HEADER"), "the header must not be sent when every line was filtered out");
    }

    @FinalCMD(aliases = "allrestrictedhelp", helpHeader = "MY CUSTOM HEADER")
    public static class AllRestrictedHelpCmd {
        @FinalCMD.SubCMD(subcmd = "one", permission = "allrestrictedhelp.one")
        public void one(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "two", permission = "allrestrictedhelp.two")
        public void two(FCommandSender sender) {}
    }

    // ------------------------------------------------------------------
    // a class's own @FCLocale static LocaleMessage fields are loaded at registration, and
    // ${label} is resolved at dispatch time (prepareClassLocales)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "classlocale")
    public static class ClassLocaleFieldCmd {
        @FCLocale(lang = LocaleType.EN_US, text = "You used ${label}!")
        static LocaleMessage GREETING;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            GREETING.send(sender);
        }
    }

    @Test
    void classOwnFCLocaleFieldsAreLoadedAndLabelIsResolvedAtDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new ClassLocaleFieldCmd());
        assertNotNull(ClassLocaleFieldCmd.GREETING, "the field should have been populated at registration time");

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "sub");

        sender.assertAnyMessageContains("You used classlocale!");
    }

    // ------------------------------------------------------------------
    // a help longer than one page is paged, and the page word rides the help word
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "pagedhelp")
    public static class PagedHelpCmd {
        @FinalCMD.SubCMD(subcmd = "alpha") public void alpha(FCommandSender sender) {}
        @FinalCMD.SubCMD(subcmd = "bravo") public void bravo(FCommandSender sender) {}
        @FinalCMD.SubCMD(subcmd = "charlie") public void charlie(FCommandSender sender) {}
        @FinalCMD.SubCMD(subcmd = "delta") public void delta(FCommandSender sender) {}
        @FinalCMD.SubCMD(subcmd = "echo") public void echo(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "onepagehelp")
    public static class OnePageHelpCmd {
        @FinalCMD.SubCMD(subcmd = "alpha") public void alpha(FCommandSender sender) {}
        @FinalCMD.SubCMD(subcmd = "bravo") public void bravo(FCommandSender sender) {}
    }

    @Test
    void aHelpLongerThanOnePageIsPagedAndTheFooterNamesTheNextPage() {
        int originalPageSize = ECSettings.COMMAND_HELP_PAGE_SIZE;
        ECSettings.COMMAND_HELP_PAGE_SIZE = 2;
        try {
            FinalCMDPluginCommand command = newHarness().register(new PagedHelpCmd());

            TestCommandSender firstPage = new TestCommandSender("console");
            harness.dispatch(command, firstPage, "help");
            assertTrue(firstPage.anyMessageContains("alpha"));
            assertFalse(firstPage.anyMessageContains("charlie"), "the third line belongs to page 2");
            assertTrue(firstPage.anyMessageContains("/pagedhelp help 2"), "the footer says how to move on");

            TestCommandSender secondPage = new TestCommandSender("console");
            harness.dispatch(command, secondPage, "help 2");
            assertTrue(secondPage.anyMessageContains("charlie"));
            assertFalse(secondPage.anyMessageContains("alpha"), "page 2 starts where page 1 stopped");

            TestCommandSender wayPastTheEnd = new TestCommandSender("console");
            harness.dispatch(command, wayPastTheEnd, "help 99");
            assertTrue(wayPastTheEnd.anyMessageContains("echo"), "a page nobody has still answers with the last one");
        } finally {
            ECSettings.COMMAND_HELP_PAGE_SIZE = originalPageSize;
        }
    }

    @Test
    void aHelpThatFitsInOnePageSaysNothingAboutPages() {
        int originalPageSize = ECSettings.COMMAND_HELP_PAGE_SIZE;
        ECSettings.COMMAND_HELP_PAGE_SIZE = 2;
        try {
            FinalCMDPluginCommand command = newHarness().register(new OnePageHelpCmd());
            TestCommandSender sender = new TestCommandSender("console");

            harness.dispatch(command, sender, "help");

            assertTrue(sender.anyMessageContains("alpha") && sender.anyMessageContains("bravo"));
            assertFalse(sender.anyMessageContains("1/1"), "one page is not worth announcing");
        } finally {
            ECSettings.COMMAND_HELP_PAGE_SIZE = originalPageSize;
        }
    }

    // ------------------------------------------------------------------
    // the help words come from configuration, and a server may add its own language
    // ------------------------------------------------------------------

    @Test
    void aConfiguredHelpWordOpensTheHelpAndTheShippedOnesStillDo() {
        try {
            HelpWords.configure(Arrays.asList("help", "?", "ajuda", "ayuda"));
            FinalCMDPluginCommand command = newHarness().register(new OnePageHelpCmd());

            TestCommandSender spanish = new TestCommandSender("console");
            harness.dispatch(command, spanish, "ayuda");
            assertTrue(spanish.anyMessageContains("alpha"), "the configured word opens the help");

            TestCommandSender english = new TestCommandSender("console");
            harness.dispatch(command, english, "help");
            assertTrue(english.anyMessageContains("alpha"), "and the shipped ones keep working");
        } finally {
            HelpWords.configure(HelpWords.DEFAULTS);
        }
    }

    @Test
    void anEmptyHelpWordListFallsBackToTheShippedWords() {
        try {
            HelpWords.configure(Arrays.asList("", "   "));

            assertEquals(HelpWords.DEFAULTS, HelpWords.all(), "an empty list is not a way to turn the help off");
            assertTrue(HelpWords.isHelpWord("HELP"), "and the lookup stays case-insensitive");
        } finally {
            HelpWords.configure(HelpWords.DEFAULTS);
        }
    }

    // ------------------------------------------------------------------
    // "is this a player?" has ONE answer, and every surface reads it
    // ------------------------------------------------------------------

    /** A sender with a UUID that is NOT an FPlayer - a command block, a proxy, a test double. */
    private static class UuidCarryingSender extends TestCommandSender {
        private final UUID uniqueId = UUID.randomUUID();

        UuidCarryingSender() {
            super("commandblock");
        }

        @Override
        public UUID getUniqueId() {
            return uniqueId;
        }
    }

    /** Contextual, and player-only - which is what gates the visibility of a line in help and tab. */
    public static class PlayerOnlyParser extends ArgParserContextual<String> {
        public PlayerOnlyParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ContextualParseCall call) {
            return ParseResult.of(call.getSender().getName());
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return true;
        }
    }

    @FinalCMD(aliases = "playeronlyline")
    public static class PlayerOnlyLineCmd {
        @FinalCMD.SubCMD(subcmd = "only")
        public void only(FCommandSender sender, @Arg.Contextual(value = "player", parser = PlayerOnlyParser.class) String who) {}
    }

    @Test
    void aSenderWithAUuidIsAPlayerToTheHelpAndToTheTabAlike() {
        FinalCMDPluginCommand command = newHarness().register(new PlayerOnlyLineCmd());
        UuidCarryingSender sender = new UuidCarryingSender();

        harness.dispatch(command, sender, "help");
        assertTrue(sender.anyMessageContains("only"), "the help lists a player-only line for a sender with a UUID");

        assertEquals(Arrays.asList("only"), harness.tab(command, sender, ""), "and so does the tab");
    }
}
