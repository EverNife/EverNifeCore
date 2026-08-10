package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the UX surface of {@code @Arg.Flag}: the compact "[--name]" usage token and its own hover
 * block (with/without locale, aliases in the title, {@code showOnUsage=false} hiding it from both),
 * and the flag-aware branches of {@code FinalCMDPluginCommand.tabComplete}.
 */
class FlagUxSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("FlagUx", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // a visible flag adds its compact "[--name]" token to the usage line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagusagetoken")
    public static class UsageToken_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg("<CooldownID>") String cooldownId,
                         @Arg.Flag(value = "--network", aliases = "-n") Boolean network) {}
    }

    @Test
    void visibleFlagAddsItsCompactTokenToUsage() {
        FinalCMDPluginCommand command = newHarness().register(new UsageToken_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("[--network]");
    }

    // ------------------------------------------------------------------
    // a flag with locales() gets its own hover block, title + bullet line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flaglocale")
    public static class FlagLocale_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg("<CooldownID>") String cooldownId,
                         @Arg.Flag(value = "--network",
                                 locales = {@FCLocale(lang = LocaleType.EN_US, text = "Apply network-wide")})
                         Boolean network) {}
    }

    @Test
    void flagWithLocaleGetsItsOwnHoverBlock() {
        FinalCMDPluginCommand command = newHarness().register(new FlagLocale_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("flaglocale set");
        assertNotNull(hover);
        assertTrue(hover.contains("✯"));
        assertTrue(hover.contains("--network"));
        assertTrue(hover.contains("Apply network-wide"));
    }

    // ------------------------------------------------------------------
    // a flag WITHOUT locales() still gets a title-only hover block (unlike a plain @Arg, which
    // contributes nothing to the hover when it has no locale)
    // ------------------------------------------------------------------

    @Test
    void flagWithoutLocaleShowsATitleOnlyHoverBlock() {
        FinalCMDPluginCommand command = newHarness().register(new UsageToken_Cmd()); //--network has no locales()
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("flagusagetoken set");
        assertNotNull(hover);
        assertTrue(hover.contains("✯"));
        assertTrue(hover.contains("--network"));
        assertFalse(hover.contains("●"), "no locale text -> no bullet line, title-only block");
    }

    // ------------------------------------------------------------------
    // aliases are appended to the hover title: "[--network | -n]"
    // ------------------------------------------------------------------

    @Test
    void aliasesAreAppendedToTheHoverTitle() {
        FinalCMDPluginCommand command = newHarness().register(new UsageToken_Cmd()); //aliases = "-n"
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("flagusagetoken set");
        assertNotNull(hover);
        assertTrue(hover.contains("--network | -n"));
    }

    // ------------------------------------------------------------------
    // showOnUsage=false hides the flag from BOTH the usage line and the hover, but it remains
    // suggested on tab-complete and fully functional on dispatch
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flaghidden")
    public static class HiddenFlag_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg("<CooldownID>") String cooldownId,
                         @Arg.Flag(value = "--hidden", showOnUsage = false,
                                 locales = {@FCLocale(lang = LocaleType.EN_US, text = "Should never render")})
                         Boolean hidden) {
            received = hidden;
        }
    }

    @Test
    void showOnUsageFalseHidesFromUsageAndHover() {
        FinalCMDPluginCommand command = newHarness().register(new HiddenFlag_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        assertFalse(sender.anyMessageContains("--hidden"), "must not appear on the usage line");
        String hover = sender.hoverTextOfMessageContaining("flaghidden set");
        if (hover != null){
            assertFalse(hover.contains("--hidden"), "must not appear on the hover either");
        }
    }

    @Test
    void showOnUsageFalseStillTabCompletesAndWorksOnDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new HiddenFlag_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //still suggested on tab
        assertEquals(List.of("--hidden"), harness.tab(command, sender, "set", "MyCd", "--"));

        //still functional on dispatch
        HiddenFlag_Cmd.received = null;
        harness.dispatch(command, sender, "set MyCd --hidden");
        assertEquals(Boolean.TRUE, HiddenFlag_Cmd.received);
    }

    // ------------------------------------------------------------------
    // the flag-aware branches of FinalCMDPluginCommand.tabComplete
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagtab")
    public static class FlagTab_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg(value = "<CooldownID>", context = "coola|coolb") String cooldownId,
                         @Arg(value = "<mode>", context = "alpha|beta|gamma") String mode,
                         @Arg.Flag(value = "--network", aliases = "-n") Boolean network,
                         @Arg.Flag(value = "--page", context = "1|2|3") Integer page,
                         @Arg.Flag(value = "--secret", permission = "flagtab.secret") Boolean secret) {}
    }

    @Test
    void dashPrefixSuggestsTheDeclaredFlagNames() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console"); //lacks "flagtab.secret"

        assertEquals(List.of("--network", "--page"), harness.tab(command, sender, "set", "MyCd", "alpha", "--"));
    }

    @Test
    void typedPrefixFiltersTheFlagNames() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("--network"), harness.tab(command, sender, "set", "MyCd", "alpha", "--ne"));
    }

    @Test
    void aFlagAlreadyUsedIsNotSuggestedAgain() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("--page"), harness.tab(command, sender, "set", "MyCd", "alpha", "--network", "--"));
    }

    @Test
    void aFlagWithDeniedPermissionIsNeverSuggested() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender withoutPermission = new TestCommandSender("console");
        TestCommandSender withPermission = new TestCommandSender("op").grant("flagtab.secret");

        assertFalse(harness.tab(command, withoutPermission, "set", "MyCd", "alpha", "--").contains("--secret"));
        assertTrue(harness.tab(command, withPermission, "set", "MyCd", "alpha", "--").contains("--secret"));
    }

    @Test
    void previousTokenIsAValueFlagDelegatesToItsOwnValueParser() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("1", "2", "3"), harness.tab(command, sender, "set", "MyCd", "alpha", "--page", ""));
    }

    @Test
    void indexCorrectionSkipsAPresenceFlagAndLandsOnTheFirstPositional() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //--network is arity 0 (never consumes a token): the word being typed is still positional 1
        //(CooldownID), NOT positional 2 (mode) - a broken index correction would answer with mode's
        //choices (alpha/beta/gamma) instead
        assertEquals(List.of("coola", "coolb"), harness.tab(command, sender, "set", "--network", ""));
    }

    @Test
    void afterEndOfFlagsEverythingStaysPositionalEvenIfItLooksLikeAFlag() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"--p" looks like the start of "--page", but it comes after a literal "--": if the end-of-flags
        //guard were broken this would answer ["--page"] instead of falling through to CooldownID's own
        //choices (which do not start with "--p", so the correct answer is empty)
        assertTrue(harness.tab(command, sender, "set", "--", "--p").isEmpty());
    }

    @Test
    void aPrefixOnlyAnAliasMatchesSuggestsThatAlias() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"-n" is the whole reason the alias exists, and it is the one moment it can be discovered
        assertEquals(List.of("-n"), harness.tab(command, sender, "set", "MyCd", "alpha", "-n"));
    }

    @Test
    void aFlagAnswersWithOneSpellingAtATime() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"-" matches both "--network" and "-n"; the long name is the one offered, never both
        assertEquals(List.of("--network", "--page"), harness.tab(command, sender, "set", "MyCd", "alpha", "-"));
    }

    @Test
    void aMarkerBeingTypedWithItsValueGluedOnCompletesTheValue() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"--page=" is not a name being typed - the name is finished and the VALUE is what is open, so
        //the flag's own parser answers, and the marker comes back glued on because a completion
        //replaces the whole word
        assertEquals(List.of("--page=1", "--page=2", "--page=3"), harness.tab(command, sender, "set", "MyCd", "alpha", "--page="));
        assertEquals(List.of("--page=2"), harness.tab(command, sender, "set", "MyCd", "alpha", "--page=2"));
    }

    @Test
    void aMarkerThatAlreadyTookItsValueLeavesTheNextWordPositional() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"--page=3" consumed nothing after it, so the word being typed is positional 0 (CooldownID) -
        //treating the marker as if it still owed a value would answer with the page choices instead
        assertEquals(List.of("coola", "coolb"), harness.tab(command, sender, "set", "--page=3", ""));
    }

    @Test
    void aMarkerTheExtractionCouldNotTakeIsStillNotAPositional() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"--page" was left with nothing to take (a flag marker follows it), so the extraction reports
        //it and leaves it among the tokens. It is a flag the sender typed either way: counting it as a
        //positional would push the word being typed onto <mode> instead of <CooldownID>
        assertEquals(List.of("coola", "coolb"), harness.tab(command, sender, "set", "--page", "--network", ""));
    }

    // ------------------------------------------------------------------
    // What a mistyped flag is answered with: every unknown marker at once, and only the flags this
    // sender could have discovered anywhere else
    // ------------------------------------------------------------------

    @Test
    void everyUnknownFlagIsNamedInTheSameMessage() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "set MyCd alpha --froce --paeg");

        sender.assertAnyMessageContains("--froce");
        sender.assertAnyMessageContains("--paeg");
    }

    @Test
    void aFlagKeptOffTheUsageLineIsNotRevealedByATypo() {
        FinalCMDPluginCommand command = newHarness().register(new HiddenFlag_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "set MyCd --hiden");

        sender.assertAnyMessageContains("--hiden");
        assertFalse(sender.anyMessageContains("--hidden"), "a hidden flag stays hidden on the error list too: " + sender.getMessages());
    }

    // ------------------------------------------------------------------
    // The tab stops offering flag names exactly where the dispatch stops reading them: once the
    // variadic tail has opened, a suggested "--loud" would be handed over as part of the message
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "tailflagcmd")
    public static class TailFlag_Cmd {
        @FinalCMD.SubCMD(subcmd = "say")
        public void say(FCommandSender sender,
                        @Arg(value = "<channel>", context = "global|local") String channel,
                        @Arg.Flag("--loud") Boolean loud,
                        @Arg("<message...>") String message) {}
    }

    @Test
    void flagNamesAreOfferedRightUpToTheTailAndNotInsideIt() {
        FinalCMDPluginCommand command = newHarness().register(new TailFlag_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //before <channel> and between <channel> and the tail, the scan is still running
        assertEquals(List.of("--loud"), harness.tab(command, sender, "say", "--"));
        assertEquals(List.of("--loud"), harness.tab(command, sender, "say", "global", "--"));

        //the first word of the message opened the tail: from here on "--" is the sender's own text
        assertTrue(harness.tab(command, sender, "say", "global", "hello", "--").isEmpty());
    }

    @Test
    void aFlagTheSenderMayNotUseIsNotNamedByATypoEither() {
        FinalCMDPluginCommand command = newHarness().register(new FlagTab_Cmd());
        TestCommandSender sender = new TestCommandSender("console"); //lacks "flagtab.secret"

        harness.dispatch(command, sender, "set MyCd alpha --secrt");

        assertFalse(sender.anyMessageContains("--secret"), "a flag the tab hides is not named here either: " + sender.getMessages());
    }
}
