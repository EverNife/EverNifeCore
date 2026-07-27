package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FlagArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the declarative {@code @FlagArg} pipeline (matrix FL): typed flag bindings on a
 * {@code CMDMethodInterpreter}, resolved by the same {@code ArgParser}s as {@code @Arg}, extracted
 * BEFORE any positional parse.
 */
class FlagArgSystemTest {

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
        harness = new FinalCmdTestHarness("FlagArg", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // FL1 - Boolean presence: "--force" -> TRUE; absent without def -> null; absent with def
    // "false" -> FALSE
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl1cmd")
    public static class FL1_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--force") Boolean force) {
            received = force;
        }
    }

    @FinalCMD(aliases = "fl1defcmd")
    public static class FL1Def_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--force", def = "false") Boolean force) {
            received = force;
        }
    }

    @Test
    void fl1_presentBooleanFlagIsTrue() {
        FinalCMDPluginCommand command = newHarness().register(new FL1_Cmd());
        FL1_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --force");

        assertEquals(Boolean.TRUE, FL1_Cmd.received);
    }

    @Test
    void fl1_absentBooleanFlagWithoutDefIsNull() {
        FinalCMDPluginCommand command = newHarness().register(new FL1_Cmd());
        FL1_Cmd.received = Boolean.TRUE; //non-null sentinel, so a leftover value can't fake a pass

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertNull(FL1_Cmd.received);
    }

    @Test
    void fl1_absentBooleanFlagWithDefFalseIsFalse() {
        FinalCMDPluginCommand command = newHarness().register(new FL1Def_Cmd());
        FL1Def_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals(Boolean.FALSE, FL1Def_Cmd.received);
    }

    // ------------------------------------------------------------------
    // FL2 - Aridade 0: a Boolean flag NEVER consumes the following token, even if it looks like a
    // legitimate value - this is the key behavioral difference from the manual/sniffed tokenizer
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl2cmd")
    public static class FL2_Cmd {
        static Boolean forceReceived;
        static String nameReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "[name]") String name, @FlagArg(name = "--force") Boolean force) {
            forceReceived = force;
            nameReceived = name;
        }
    }

    @Test
    void fl2_booleanFlagArityZeroNeverConsumesTheNextToken() {
        FinalCMDPluginCommand command = newHarness().register(new FL2_Cmd());
        FL2_Cmd.forceReceived = null;
        FL2_Cmd.nameReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --force Nick");

        assertEquals(Boolean.TRUE, FL2_Cmd.forceReceived);
        assertEquals("Nick", FL2_Cmd.nameReceived, "Nick was NOT consumed by --force; it remained positional");
    }

    // ------------------------------------------------------------------
    // FL3 - value flag: consumes exactly one token, honors context bounds, and a bad value aborts
    // dispatch (the same ArgParserNumber error a REQUIRED positional would raise)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl3cmd")
    public static class FL3_Cmd {
        static Integer received;
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--page", context = "[1:*]") Integer page) {
            invoked = true;
            received = page;
        }
    }

    @Test
    void fl3_valueFlagConsumesTheNextToken() {
        FinalCMDPluginCommand command = newHarness().register(new FL3_Cmd());
        FL3_Cmd.invoked = false;
        FL3_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --page 2");

        assertTrue(FL3_Cmd.invoked);
        assertEquals(2, FL3_Cmd.received);
    }

    @Test
    void fl3_unparseableValueAbortsDispatchAndDoesNotInvoke() {
        FinalCMDPluginCommand command = newHarness().register(new FL3_Cmd());
        FL3_Cmd.invoked = false;

        harness.dispatch(command, new TestCommandSender("console"), "sub --page abc");

        assertFalse(FL3_Cmd.invoked);
    }

    @Test
    void fl3_valueOutsideTheContextBoundAbortsDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new FL3_Cmd());
        FL3_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --page 0");

        assertFalse(FL3_Cmd.invoked);
        sender.assertAnyMessageContains("higher than");
    }

    // ------------------------------------------------------------------
    // FL4 - def(): absent with a valid def parses it through the same parser; an invalid def
    // errors at first use, UNLIKE @Arg.def() (see ArgDefSystemTest scenario3) - a flag's def is fed
    // through an internally-REQUIRED ArgInfo so a typo is never silently swallowed
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl4cmd")
    public static class FL4_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--page", def = "1") Integer page) {
            received = page;
        }
    }

    @FinalCMD(aliases = "fl4invalidcmd")
    public static class FL4Invalid_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--page", def = "abc") Integer page) {
            invoked = true;
        }
    }

    @Test
    void fl4_absentFlagWithValidDefParsesTheDef() {
        FinalCMDPluginCommand command = newHarness().register(new FL4_Cmd());
        FL4_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals(1, FL4_Cmd.received);
    }

    @Test
    void fl4_invalidDefErrorsAtFirstUseInsteadOfSilentlyResolvingToNull() {
        FinalCMDPluginCommand command = newHarness().register(new FL4Invalid_Cmd());
        FL4Invalid_Cmd.invoked = false;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertFalse(FL4Invalid_Cmd.invoked);
    }

    // ------------------------------------------------------------------
    // FL5 - aliases: a short alias resolves the same binding as the long name; a duplicate
    // alias/name spelling shared between two @FlagArg on the same method fails registration
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl5cmd")
    public static class FL5_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--force", aliases = "-f") Boolean force) {
            received = force;
        }
    }

    public static class FL5Dup_Cmd {
        @FinalCMD(aliases = "fl5dupcmd")
        public void run(FCommandSender sender,
                         @FlagArg(name = "--force", aliases = "-x") Boolean force,
                         @FlagArg(name = "--fx", aliases = "-x") Boolean fx) {}
    }

    @Test
    void fl5_aliasResolvesTheSameBindingAsTheLongName() {
        FinalCMDPluginCommand command = newHarness().register(new FL5_Cmd());
        FL5_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub -f");

        assertEquals(Boolean.TRUE, FL5_Cmd.received);
    }

    @Test
    void fl5_duplicateAliasAcrossTwoFlagsFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new FL5Dup_Cmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // FL6 - three independent fail-fast registration guards
    // ------------------------------------------------------------------

    public static class FL6Primitive_Cmd {
        @FinalCMD(aliases = "fl6primcmd")
        public void run(FCommandSender sender, @FlagArg(name = "--force") boolean force) {}
    }

    public static class FL6Both_Cmd {
        @FinalCMD(aliases = "fl6bothcmd")
        public void run(FCommandSender sender, @Arg(name = "<x>") @FlagArg(name = "--x") String x) {}
    }

    public static class FL6BadName_Cmd {
        @FinalCMD(aliases = "fl6badnamecmd")
        public void run(FCommandSender sender, @FlagArg(name = "force") String force) {}
    }

    @Test
    void fl6a_primitiveFlagTypeFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new FL6Primitive_Cmd());

        assertFalse(registered);
    }

    @Test
    void fl6b_argAndFlagArgOnTheSameParameterFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new FL6Both_Cmd());

        assertFalse(registered);
    }

    @Test
    void fl6c_nameWithoutLongDashesFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new FL6BadName_Cmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // FL7 - an undeclared flag marker aborts dispatch with a message listing the declared flags;
    // the same token after "--" is not an error at all, it stays positional
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl7cmd")
    public static class FL7_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--force") Boolean force) {
            invoked = true;
        }
    }

    @Test
    void fl7_unknownFlagAbortsDispatchAndListsTheDeclaredFlags() {
        FinalCMDPluginCommand command = newHarness().register(new FL7_Cmd());
        FL7_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --froce");

        assertFalse(FL7_Cmd.invoked);
        sender.assertAnyMessageContains("froce");
        sender.assertAnyMessageContains("--force");
    }

    @Test
    void fl7_theSameTokenAfterEndOfFlagsMarkerIsPositionalNotAnError() {
        FinalCMDPluginCommand command = newHarness().register(new FL7_Cmd());
        FL7_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub -- --froce");

        assertTrue(FL7_Cmd.invoked);
        sender.assertNoMessageSent();
    }

    // ------------------------------------------------------------------
    // FL8 - per-flag permission: checked only when the flag is actually present; a standard
    // permission message aborts dispatch
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl8cmd")
    public static class FL8_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--silent", permission = "test.silent") Boolean silent) {
            invoked = true;
        }
    }

    @Test
    void fl8_presentFlagWithoutThePermissionAbortsWithTheStandardMessage() {
        FinalCMDPluginCommand command = newHarness().register(new FL8_Cmd());
        FL8_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --silent");

        assertFalse(FL8_Cmd.invoked);
        sender.assertAnyMessageContains("permission");
    }

    @Test
    void fl8_presentFlagWithThePermissionInvokesNormally() {
        FinalCMDPluginCommand command = newHarness().register(new FL8_Cmd());
        FL8_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console").grant("test.silent");

        harness.dispatch(command, sender, "sub --silent");

        assertTrue(FL8_Cmd.invoked);
    }

    @Test
    void fl8_absentFlagNeverChecksThePermission() {
        FinalCMDPluginCommand command = newHarness().register(new FL8_Cmd());
        FL8_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console"); //no permission granted

        harness.dispatch(command, sender, "sub");

        assertTrue(FL8_Cmd.invoked);
    }

    // ------------------------------------------------------------------
    // FL9 - anti-false-positive invariant: a method without @FlagArg never enters flag mode
    // ("--x" stays positional); a method WITH @FlagArg still parses a negative number positional
    // correctly (the negative-number guard is untouched by the declarative pipeline)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl9nocmd")
    public static class FL9NoFlag_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "[value]") String value) {
            received = value;
        }
    }

    @FinalCMD(aliases = "fl9negcmd")
    public static class FL9Negative_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "[value]") Integer value, @FlagArg(name = "--force") Boolean force) {
            received = value;
        }
    }

    @Test
    void fl9_methodWithoutFlagArgTreatsDashTokensAsPositional() {
        FinalCMDPluginCommand command = newHarness().register(new FL9NoFlag_Cmd());
        FL9NoFlag_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --qualquer");

        assertEquals("--qualquer", FL9NoFlag_Cmd.received);
    }

    @Test
    void fl9_methodWithFlagArgStillParsesANegativeNumberPositional() {
        FinalCMDPluginCommand command = newHarness().register(new FL9Negative_Cmd());
        FL9Negative_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub -5");

        assertEquals(-5, FL9Negative_Cmd.received);
    }

    // ------------------------------------------------------------------
    // FL10 - flags can sit before, between, or after positionals on the command line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl10cmd")
    public static class FL10_Cmd {
        static String cooldownIdReceived;
        static String extraReceived;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "reset")
        public void reset(FCommandSender sender,
                           @Arg(name = "<CooldownID>") String cooldownId,
                           @Arg(name = "<Extra>") String extra,
                           @FlagArg(name = "--force", aliases = "-f") Boolean force) {
            cooldownIdReceived = cooldownId;
            extraReceived = extra;
            forceReceived = force;
        }
    }

    private void assertFl10(String argsLine) {
        FinalCMDPluginCommand command = harness.register(new FL10_Cmd());
        FL10_Cmd.cooldownIdReceived = null;
        FL10_Cmd.extraReceived = null;
        FL10_Cmd.forceReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), argsLine);

        assertEquals("MyCooldown", FL10_Cmd.cooldownIdReceived, argsLine);
        assertEquals("Extra", FL10_Cmd.extraReceived, argsLine);
        assertEquals(Boolean.TRUE, FL10_Cmd.forceReceived, argsLine);
    }

    @Test
    void fl10_flagBeforeThePositionals() {
        newHarness();
        assertFl10("reset --force MyCooldown Extra");
    }

    @Test
    void fl10_flagBetweenThePositionals() {
        newHarness();
        assertFl10("reset MyCooldown --force Extra");
    }

    @Test
    void fl10_flagAfterThePositionals() {
        newHarness();
        assertFl10("reset MyCooldown Extra --force");
    }

    // ------------------------------------------------------------------
    // FL11 - Cases A/B (quoted vs unquoted multi-word value) apply identically to a @FlagArg
    // String, and the value/quote consumption never eats the following positional in Case A
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl11cmd")
    public static class FL11_Cmd {
        static String titleReceived;
        static String restReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--title") String title, @Arg(name = "[rest]") String rest) {
            titleReceived = title;
            restReceived = rest;
        }
    }

    @Test
    void fl11_casoA_quotedMultiWordFlagValueBecomesASingleValue() {
        FinalCMDPluginCommand command = newHarness().register(new FL11_Cmd());
        FL11_Cmd.titleReceived = null;
        FL11_Cmd.restReceived = "sentinel";

        harness.dispatch(command, new TestCommandSender("console"), "sub --title 'Title Message'");

        assertEquals("Title Message", FL11_Cmd.titleReceived);
        assertNull(FL11_Cmd.restReceived);
    }

    @Test
    void fl11_casoB_unquotedFlagValueOnlyTakesTheNextTokenAndLeavesTheRestPositional() {
        FinalCMDPluginCommand command = newHarness().register(new FL11_Cmd());
        FL11_Cmd.titleReceived = null;
        FL11_Cmd.restReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --title Title Message");

        assertEquals("Title", FL11_Cmd.titleReceived);
        assertEquals("Message", FL11_Cmd.restReceived, "'Message' was NOT part of the flag value - it remains a positional argument");
    }

    // ------------------------------------------------------------------
    // FL12 - isFlag(): a custom parser sees true when parsing a flag's value, false when parsing a
    // positional's value, through the SAME ArgParserCommandContext contract
    // ------------------------------------------------------------------

    public static class IsFlagTrackingParser extends ArgParser<String> {
        static Boolean lastIsFlag;

        public IsFlagTrackingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public String parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {
            lastIsFlag = argContext.isFlag();
            return argumento.toString();
        }
    }

    @FinalCMD(aliases = "fl12flagcmd")
    public static class FL12Flag_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--tag", parser = IsFlagTrackingParser.class) String tag) {}
    }

    @FinalCMD(aliases = "fl12poscmd")
    public static class FL12Positional_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "[tag]", parser = IsFlagTrackingParser.class) String tag) {}
    }

    @Test
    void fl12_isFlagIsTrueWhenParsingAFlagsValue() {
        FinalCMDPluginCommand command = newHarness().register(new FL12Flag_Cmd());
        IsFlagTrackingParser.lastIsFlag = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --tag hello");

        assertEquals(Boolean.TRUE, IsFlagTrackingParser.lastIsFlag);
    }

    @Test
    void fl12_isFlagIsFalseWhenParsingAPositionalsValue() {
        FinalCMDPluginCommand command = newHarness().register(new FL12Positional_Cmd());
        IsFlagTrackingParser.lastIsFlag = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub hello");

        assertEquals(Boolean.FALSE, IsFlagTrackingParser.lastIsFlag);
    }

    // ------------------------------------------------------------------
    // FL13 - a MultiArgumentos contextual parameter on a method with @Arg+@FlagArg sees only the
    // post-strip positionals (the flag tokens are already gone)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl13cmd")
    public static class FL13_Cmd {
        static List<String> seenPositionals;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "[value]") String value, @FlagArg(name = "--force") Boolean force, MultiArgumentos args) {
            seenPositionals = args.getStringArgs();
            forceReceived = force;
        }
    }

    @Test
    void fl13_multiArgumentosContextualSeesOnlyThePostStripPositionals() {
        FinalCMDPluginCommand command = newHarness().register(new FL13_Cmd());
        FL13_Cmd.seenPositionals = null;
        FL13_Cmd.forceReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub hello --force");

        assertEquals(List.of("sub", "hello"), FL13_Cmd.seenPositionals);
        assertEquals(Boolean.TRUE, FL13_Cmd.forceReceived);
    }

    // ------------------------------------------------------------------
    // FL14 - two @FlagArg on the same method resolve independently of declaration order on the
    // command line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "fl14cmd")
    public static class FL14_Cmd {
        static Boolean alphaReceived;
        static Boolean betaReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @FlagArg(name = "--alpha") Boolean alpha, @FlagArg(name = "--beta") Boolean beta) {
            alphaReceived = alpha;
            betaReceived = beta;
        }
    }

    @Test
    void fl14_bothFlagsInDeclarationOrder() {
        FinalCMDPluginCommand command = newHarness().register(new FL14_Cmd());
        FL14_Cmd.alphaReceived = null;
        FL14_Cmd.betaReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --alpha --beta");

        assertEquals(Boolean.TRUE, FL14_Cmd.alphaReceived);
        assertEquals(Boolean.TRUE, FL14_Cmd.betaReceived);
    }

    @Test
    void fl14_bothFlagsInReverseOrder() {
        FinalCMDPluginCommand command = newHarness().register(new FL14_Cmd());
        FL14_Cmd.alphaReceived = null;
        FL14_Cmd.betaReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --beta --alpha");

        assertEquals(Boolean.TRUE, FL14_Cmd.alphaReceived);
        assertEquals(Boolean.TRUE, FL14_Cmd.betaReceived);
    }

    @Test
    void fl14_onlyOneOfTwoFlagsPresentLeavesTheOtherNull() {
        FinalCMDPluginCommand command = newHarness().register(new FL14_Cmd());
        FL14_Cmd.alphaReceived = Boolean.TRUE; //sentinel
        FL14_Cmd.betaReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --beta");

        assertNull(FL14_Cmd.alphaReceived);
        assertEquals(Boolean.TRUE, FL14_Cmd.betaReceived);
    }

    // ------------------------------------------------------------------
    // R1 - array arithmetic: a @FlagArg declared BETWEEN two @Arg in the METHOD signature must not
    // disturb theArgs[] slot assignment for either positional
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "r1cmd")
    public static class R1_Cmd {
        static String beforeReceived;
        static String afterReceived;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<before>") String before, @FlagArg(name = "--force") Boolean force, @Arg(name = "<after>") String after) {
            beforeReceived = before;
            forceReceived = force;
            afterReceived = after;
        }
    }

    @Test
    void r1_flagDeclaredBetweenTwoPositionalsInTheMethodSignatureDoesNotDisturbTheArgsSlots() {
        FinalCMDPluginCommand command = newHarness().register(new R1_Cmd());
        R1_Cmd.beforeReceived = null;
        R1_Cmd.afterReceived = null;
        R1_Cmd.forceReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub A B --force");

        assertEquals("A", R1_Cmd.beforeReceived);
        assertEquals("B", R1_Cmd.afterReceived);
        assertEquals(Boolean.TRUE, R1_Cmd.forceReceived);
    }
}
