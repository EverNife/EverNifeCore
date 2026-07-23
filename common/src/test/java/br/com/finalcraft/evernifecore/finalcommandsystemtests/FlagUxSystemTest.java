package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FlagArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestCommandSender;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the F6 UX surface of {@code @FlagArg}: the compact "[--name]" usage token and its own hover
 * block (with/without locale, aliases in the title, {@code showOnUsage=false} hiding it from both),
 * and the flag-aware branches of {@code FinalCMDPluginCommand.tabComplete} (matrix TA).
 */
class FlagUxSystemTest {

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
        harness = new FinalCmdTestHarness("FlagUx", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // H1 - a visible flag adds its compact "[--name]" token to the usage line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "h1cmd")
    public static class H1_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg(name = "<CooldownID>") String cooldownId,
                         @FlagArg(name = "--network", aliases = "-n") Boolean network) {}
    }

    @Test
    void h1_visibleFlagAddsItsCompactTokenToUsage() {
        FinalCMDPluginCommand command = newHarness().register(new H1_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("[--network]");
    }

    // ------------------------------------------------------------------
    // H2 - a flag with locales() gets its own hover block, title + bullet line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "h2cmd")
    public static class H2_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg(name = "<CooldownID>") String cooldownId,
                         @FlagArg(name = "--network",
                                 locales = {@FCLocale(lang = LocaleType.EN_US, text = "Apply network-wide")})
                         Boolean network) {}
    }

    @Test
    void h2_flagWithLocaleGetsItsOwnHoverBlock() {
        FinalCMDPluginCommand command = newHarness().register(new H2_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("h2cmd set");
        assertNotNull(hover);
        assertTrue(hover.contains("✯"));
        assertTrue(hover.contains("--network"));
        assertTrue(hover.contains("Apply network-wide"));
    }

    // ------------------------------------------------------------------
    // H3 - a flag WITHOUT locales() still gets a title-only hover block (unlike a plain @Arg, which
    // contributes nothing to the hover when it has no locale)
    // ------------------------------------------------------------------

    @Test
    void h3_flagWithoutLocaleShowsATitleOnlyHoverBlock() {
        FinalCMDPluginCommand command = newHarness().register(new H1_Cmd()); //--network has no locales()
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("h1cmd set");
        assertNotNull(hover);
        assertTrue(hover.contains("✯"));
        assertTrue(hover.contains("--network"));
        assertFalse(hover.contains("●"), "no locale text -> no bullet line, title-only block");
    }

    // ------------------------------------------------------------------
    // H4 - aliases are appended to the hover title: "[--network | -n]"
    // ------------------------------------------------------------------

    @Test
    void h4_aliasesAreAppendedToTheHoverTitle() {
        FinalCMDPluginCommand command = newHarness().register(new H1_Cmd()); //aliases = "-n"
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        String hover = sender.hoverTextOfMessageContaining("h1cmd set");
        assertNotNull(hover);
        assertTrue(hover.contains("--network | -n"));
    }

    // ------------------------------------------------------------------
    // H5 - showOnUsage=false hides the flag from BOTH the usage line and the hover, but it remains
    // suggested on tab-complete and fully functional on dispatch
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "h5cmd")
    public static class H5_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg(name = "<CooldownID>") String cooldownId,
                         @FlagArg(name = "--hidden", showOnUsage = false,
                                 locales = {@FCLocale(lang = LocaleType.EN_US, text = "Should never render")})
                         Boolean hidden) {
            received = hidden;
        }
    }

    @Test
    void h5_showOnUsageFalseHidesFromUsageAndHover() {
        FinalCMDPluginCommand command = newHarness().register(new H5_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "help");

        assertFalse(sender.anyMessageContains("--hidden"), "must not appear on the usage line");
        String hover = sender.hoverTextOfMessageContaining("h5cmd set");
        if (hover != null){
            assertFalse(hover.contains("--hidden"), "must not appear on the hover either");
        }
    }

    @Test
    void h5_showOnUsageFalseStillTabCompletesAndWorksOnDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new H5_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //still suggested on tab
        assertEquals(List.of("--hidden"), harness.tab(command, sender, "set", "MyCd", "--"));

        //still functional on dispatch
        H5_Cmd.received = null;
        harness.dispatch(command, sender, "set MyCd --hidden");
        assertEquals(Boolean.TRUE, H5_Cmd.received);
    }

    // ------------------------------------------------------------------
    // TA - the flag-aware branches of FinalCMDPluginCommand.tabComplete
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "tacmd")
    public static class TA_Cmd {
        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender,
                         @Arg(name = "<CooldownID>", context = "coola|coolb") String cooldownId,
                         @Arg(name = "<mode>", context = "alpha|beta|gamma") String mode,
                         @FlagArg(name = "--network", aliases = "-n") Boolean network,
                         @FlagArg(name = "--page", context = "1|2|3") Integer page,
                         @FlagArg(name = "--secret", permission = "ta.secret") Boolean secret) {}
    }

    @Test
    void ta_a_dashPrefixSuggestsTheDeclaredFlagNames() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender sender = new TestCommandSender("console"); //lacks "ta.secret"

        assertEquals(List.of("--network", "--page"), harness.tab(command, sender, "set", "MyCd", "alpha", "--"));
    }

    @Test
    void ta_b_typedPrefixFiltersTheFlagNames() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("--network"), harness.tab(command, sender, "set", "MyCd", "alpha", "--ne"));
    }

    @Test
    void ta_c_aFlagAlreadyUsedIsNotSuggestedAgain() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("--page"), harness.tab(command, sender, "set", "MyCd", "alpha", "--network", "--"));
    }

    @Test
    void ta_d_aFlagWithDeniedPermissionIsNeverSuggested() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender withoutPermission = new TestCommandSender("console");
        TestCommandSender withPermission = new TestCommandSender("op").grant("ta.secret");

        assertFalse(harness.tab(command, withoutPermission, "set", "MyCd", "alpha", "--").contains("--secret"));
        assertTrue(harness.tab(command, withPermission, "set", "MyCd", "alpha", "--").contains("--secret"));
    }

    @Test
    void ta_e_previousTokenIsAValueFlagDelegatesToItsOwnValueParser() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(List.of("1", "2", "3"), harness.tab(command, sender, "set", "MyCd", "alpha", "--page", ""));
    }

    @Test
    void ta_f_indexCorrectionSkipsAPresenceFlagAndLandsOnTheFirstPositional() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //--network is arity 0 (never consumes a token): the word being typed is still positional 1
        //(CooldownID), NOT positional 2 (mode) - a broken index correction would answer with mode's
        //choices (alpha/beta/gamma) instead
        assertEquals(List.of("coola", "coolb"), harness.tab(command, sender, "set", "--network", ""));
    }

    @Test
    void ta_g_afterEndOfFlagsEverythingStaysPositionalEvenIfItLooksLikeAFlag() {
        FinalCMDPluginCommand command = newHarness().register(new TA_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //"--p" looks like the start of "--page", but it comes after a literal "--": if the end-of-flags
        //guard were broken this would answer ["--page"] instead of falling through to CooldownID's own
        //choices (which do not start with "--p", so the correct answer is empty)
        assertTrue(harness.tab(command, sender, "set", "--", "--p").isEmpty());
    }
}
