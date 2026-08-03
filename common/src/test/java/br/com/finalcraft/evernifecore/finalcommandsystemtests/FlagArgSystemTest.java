package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
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
 * Pins the declarative {@code @Arg.Flag} pipeline: typed flag bindings on a
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
    // Boolean presence: "--force" -> TRUE; absent without def -> null; absent with def
    // "false" -> FALSE
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "boolpresence")
    public static class BooleanPresence_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--force") Boolean force) {
            received = force;
        }
    }

    @FinalCMD(aliases = "boolpresencedef")
    public static class BooleanPresenceWithDef_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--force", def = "false") Boolean force) {
            received = force;
        }
    }

    @Test
    void presentBooleanFlagIsTrue() {
        FinalCMDPluginCommand command = newHarness().register(new BooleanPresence_Cmd());
        BooleanPresence_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --force");

        assertEquals(Boolean.TRUE, BooleanPresence_Cmd.received);
    }

    @Test
    void absentBooleanFlagWithoutDefIsNull() {
        FinalCMDPluginCommand command = newHarness().register(new BooleanPresence_Cmd());
        BooleanPresence_Cmd.received = Boolean.TRUE; //non-null sentinel, so a leftover value can't fake a pass

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertNull(BooleanPresence_Cmd.received);
    }

    @Test
    void absentBooleanFlagWithDefFalseIsFalse() {
        FinalCMDPluginCommand command = newHarness().register(new BooleanPresenceWithDef_Cmd());
        BooleanPresenceWithDef_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals(Boolean.FALSE, BooleanPresenceWithDef_Cmd.received);
    }

    // ------------------------------------------------------------------
    // Arity 0: a Boolean flag NEVER consumes the following token, even if it looks like a
    // legitimate value
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "boolarityzero")
    public static class BooleanArityZero_Cmd {
        static Boolean forceReceived;
        static String nameReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("[name]") String name, @Arg.Flag("--force") Boolean force) {
            forceReceived = force;
            nameReceived = name;
        }
    }

    @Test
    void booleanFlagArityZeroNeverConsumesTheNextToken() {
        FinalCMDPluginCommand command = newHarness().register(new BooleanArityZero_Cmd());
        BooleanArityZero_Cmd.forceReceived = null;
        BooleanArityZero_Cmd.nameReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --force Nick");

        assertEquals(Boolean.TRUE, BooleanArityZero_Cmd.forceReceived);
        assertEquals("Nick", BooleanArityZero_Cmd.nameReceived, "Nick was NOT consumed by --force; it remained positional");
    }

    // ------------------------------------------------------------------
    // value flag: consumes exactly one token, honors context bounds, and a bad value aborts
    // dispatch (the same ArgParserNumber error a REQUIRED positional would raise)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "valueflag")
    public static class ValueFlag_Cmd {
        static Integer received;
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--page", context = "[1:*]") Integer page) {
            invoked = true;
            received = page;
        }
    }

    @Test
    void valueFlagConsumesTheNextToken() {
        FinalCMDPluginCommand command = newHarness().register(new ValueFlag_Cmd());
        ValueFlag_Cmd.invoked = false;
        ValueFlag_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --page 2");

        assertTrue(ValueFlag_Cmd.invoked);
        assertEquals(2, ValueFlag_Cmd.received);
    }

    @Test
    void unparseableValueAbortsDispatchAndDoesNotInvoke() {
        FinalCMDPluginCommand command = newHarness().register(new ValueFlag_Cmd());
        ValueFlag_Cmd.invoked = false;

        harness.dispatch(command, new TestCommandSender("console"), "sub --page abc");

        assertFalse(ValueFlag_Cmd.invoked);
    }

    @Test
    void valueOutsideTheContextBoundAbortsDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new ValueFlag_Cmd());
        ValueFlag_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --page 0");

        assertFalse(ValueFlag_Cmd.invoked);
        sender.assertAnyMessageContains("higher than");
    }

    // ------------------------------------------------------------------
    // def(): absent with a valid def parses it through the same parser; an invalid def
    // errors at first use, UNLIKE @Arg.def() (see ArgDefSystemTest scenario3) - a flag's def is fed
    // through an internally-REQUIRED ArgInfo so a typo is never silently swallowed
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagdef")
    public static class FlagDef_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--page", def = "1") Integer page) {
            received = page;
        }
    }

    @FinalCMD(aliases = "flagdefinvalid")
    public static class UnreadableFlagDef_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--page", def = "abc") Integer page) {
            invoked = true;
        }
    }

    @Test
    void absentFlagWithValidDefParsesTheDef() {
        FinalCMDPluginCommand command = newHarness().register(new FlagDef_Cmd());
        FlagDef_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals(1, FlagDef_Cmd.received);
    }

    @Test
    void invalidDefErrorsAtFirstUseInsteadOfSilentlyResolvingToNull() {
        FinalCMDPluginCommand command = newHarness().register(new UnreadableFlagDef_Cmd());
        UnreadableFlagDef_Cmd.invoked = false;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertFalse(UnreadableFlagDef_Cmd.invoked);
    }

    // ------------------------------------------------------------------
    // aliases: a short alias resolves the same binding as the long name; a duplicate
    // alias/name spelling shared between two @Arg.Flag on the same method fails registration
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagalias")
    public static class FlagAlias_Cmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--force", aliases = "-f") Boolean force) {
            received = force;
        }
    }

    public static class DuplicateFlagSpelling_Cmd {
        @FinalCMD(aliases = "flagaliasdup")
        public void run(FCommandSender sender,
                         @Arg.Flag(value = "--force", aliases = "-x") Boolean force,
                         @Arg.Flag(value = "--fx", aliases = "-x") Boolean fx) {}
    }

    @Test
    void aliasResolvesTheSameBindingAsTheLongName() {
        FinalCMDPluginCommand command = newHarness().register(new FlagAlias_Cmd());
        FlagAlias_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub -f");

        assertEquals(Boolean.TRUE, FlagAlias_Cmd.received);
    }

    @Test
    void duplicateAliasAcrossTwoFlagsFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new DuplicateFlagSpelling_Cmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // three independent fail-fast registration guards
    // ------------------------------------------------------------------

    public static class PrimitiveFlagType_Cmd {
        @FinalCMD(aliases = "flagprimitive")
        public void run(FCommandSender sender, @Arg.Flag("--force") boolean force) {}
    }

    public static class ArgAndFlagOnOneParameter_Cmd {
        @FinalCMD(aliases = "flagbotharg")
        public void run(FCommandSender sender, @Arg("<x>") @Arg.Flag("--x") String x) {}
    }

    public static class FlagNameWithoutDashes_Cmd {
        @FinalCMD(aliases = "flagbadname")
        public void run(FCommandSender sender, @Arg.Flag("force") String force) {}
    }

    @Test
    void primitiveFlagTypeFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new PrimitiveFlagType_Cmd());

        assertFalse(registered);
    }

    @Test
    void argAndFlagArgOnTheSameParameterFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new ArgAndFlagOnOneParameter_Cmd());

        assertFalse(registered);
    }

    @Test
    void nameWithoutLongDashesFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new FlagNameWithoutDashes_Cmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // an undeclared flag marker aborts dispatch with a message listing the declared flags;
    // the same token after "--" is not an error at all, it stays positional
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagundeclared")
    public static class UndeclaredFlag_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--force") Boolean force) {
            invoked = true;
        }
    }

    @Test
    void unknownFlagAbortsDispatchAndListsTheDeclaredFlags() {
        FinalCMDPluginCommand command = newHarness().register(new UndeclaredFlag_Cmd());
        UndeclaredFlag_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --froce");

        assertFalse(UndeclaredFlag_Cmd.invoked);
        sender.assertAnyMessageContains("froce");
        sender.assertAnyMessageContains("--force");
    }

    @Test
    void theSameTokenAfterEndOfFlagsMarkerIsPositionalNotAnError() {
        FinalCMDPluginCommand command = newHarness().register(new UndeclaredFlag_Cmd());
        UndeclaredFlag_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub -- --froce");

        assertTrue(UndeclaredFlag_Cmd.invoked);
        sender.assertNoMessageSent();
    }

    // ------------------------------------------------------------------
    // per-flag permission: checked only when the flag is actually present; a standard
    // permission message aborts dispatch
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagperm")
    public static class FlagPermission_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--silent", permission = "test.silent") Boolean silent) {
            invoked = true;
        }
    }

    @Test
    void presentFlagWithoutThePermissionAbortsWithTheStandardMessage() {
        FinalCMDPluginCommand command = newHarness().register(new FlagPermission_Cmd());
        FlagPermission_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --silent");

        assertFalse(FlagPermission_Cmd.invoked);
        sender.assertAnyMessageContains("permission");
    }

    @Test
    void presentFlagWithThePermissionInvokesNormally() {
        FinalCMDPluginCommand command = newHarness().register(new FlagPermission_Cmd());
        FlagPermission_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console").grant("test.silent");

        harness.dispatch(command, sender, "sub --silent");

        assertTrue(FlagPermission_Cmd.invoked);
    }

    @Test
    void absentFlagNeverChecksThePermission() {
        FinalCMDPluginCommand command = newHarness().register(new FlagPermission_Cmd());
        FlagPermission_Cmd.invoked = false;
        TestCommandSender sender = new TestCommandSender("console"); //no permission granted

        harness.dispatch(command, sender, "sub");

        assertTrue(FlagPermission_Cmd.invoked);
    }

    // ------------------------------------------------------------------
    // the marker syntax belongs to the LINE, not to the declaration: a method without
    // @Arg.Flag refuses "--x" instead of taking it as a positional, and the bare "--" escape is what
    // delivers it; a method WITH @Arg.Flag still parses a negative number positional correctly (the
    // negative-number guard is untouched by the declarative pipeline)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagnone")
    public static class NoFlagDeclared_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("[value]") String value) {
            received = value;
        }
    }

    @FinalCMD(aliases = "flagnegative")
    public static class NegativeNumberPositional_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("[value]") Integer value, @Arg.Flag("--force") Boolean force) {
            received = value;
        }
    }

    @Test
    void methodWithoutFlagArgRefusesADashTokenAndTeachesTheEscape() {
        FinalCMDPluginCommand command = newHarness().register(new NoFlagDeclared_Cmd());
        NoFlagDeclared_Cmd.received = null;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --qualquer");

        assertNull(NoFlagDeclared_Cmd.received);
        sender.assertAnyMessageContains("--qualquer");
        sender.assertAnyMessageContains("plain text"); //the refusal teaches the escape that makes it a positional
    }

    @Test
    void theEscapeDeliversADashTokenAsAPositionalWithNoFlagDeclaredAtAll() {
        FinalCMDPluginCommand command = newHarness().register(new NoFlagDeclared_Cmd());
        NoFlagDeclared_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub -- --qualquer");

        assertEquals("--qualquer", NoFlagDeclared_Cmd.received);
    }

    @Test
    void methodWithFlagArgStillParsesANegativeNumberPositional() {
        FinalCMDPluginCommand command = newHarness().register(new NegativeNumberPositional_Cmd());
        NegativeNumberPositional_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub -5");

        assertEquals(-5, NegativeNumberPositional_Cmd.received);
    }

    // ------------------------------------------------------------------
    // flags can sit before, between, or after positionals on the command line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagposition")
    public static class FlagPosition_Cmd {
        static String cooldownIdReceived;
        static String extraReceived;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "reset")
        public void reset(FCommandSender sender,
                           @Arg("<CooldownID>") String cooldownId,
                           @Arg("<Extra>") String extra,
                           @Arg.Flag(value = "--force", aliases = "-f") Boolean force) {
            cooldownIdReceived = cooldownId;
            extraReceived = extra;
            forceReceived = force;
        }
    }

    private void assertFlagAndBothPositionalsResolve(String argsLine) {
        FinalCMDPluginCommand command = harness.register(new FlagPosition_Cmd());
        FlagPosition_Cmd.cooldownIdReceived = null;
        FlagPosition_Cmd.extraReceived = null;
        FlagPosition_Cmd.forceReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), argsLine);

        assertEquals("MyCooldown", FlagPosition_Cmd.cooldownIdReceived, argsLine);
        assertEquals("Extra", FlagPosition_Cmd.extraReceived, argsLine);
        assertEquals(Boolean.TRUE, FlagPosition_Cmd.forceReceived, argsLine);
    }

    @Test
    void flagBeforeThePositionals() {
        newHarness();
        assertFlagAndBothPositionalsResolve("reset --force MyCooldown Extra");
    }

    @Test
    void flagBetweenThePositionals() {
        newHarness();
        assertFlagAndBothPositionalsResolve("reset MyCooldown --force Extra");
    }

    @Test
    void flagAfterThePositionals() {
        newHarness();
        assertFlagAndBothPositionalsResolve("reset MyCooldown Extra --force");
    }

    // ------------------------------------------------------------------
    // Cases A/B (quoted vs unquoted multi-word value) apply identically to a @Arg.Flag
    // String, and the value/quote consumption never eats the following positional in Case A
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagquoted")
    public static class QuotedFlagValue_Cmd {
        static String titleReceived;
        static String restReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--title") String title, @Arg("[rest]") String rest) {
            titleReceived = title;
            restReceived = rest;
        }
    }

    @Test
    void quotedMultiWordFlagValueBecomesASingleValue() {
        FinalCMDPluginCommand command = newHarness().register(new QuotedFlagValue_Cmd());
        QuotedFlagValue_Cmd.titleReceived = null;
        QuotedFlagValue_Cmd.restReceived = "sentinel";

        harness.dispatch(command, new TestCommandSender("console"), "sub --title 'Title Message'");

        assertEquals("Title Message", QuotedFlagValue_Cmd.titleReceived);
        assertNull(QuotedFlagValue_Cmd.restReceived);
    }

    @Test
    void unquotedFlagValueOnlyTakesTheNextTokenAndLeavesTheRestPositional() {
        FinalCMDPluginCommand command = newHarness().register(new QuotedFlagValue_Cmd());
        QuotedFlagValue_Cmd.titleReceived = null;
        QuotedFlagValue_Cmd.restReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --title Title Message");

        assertEquals("Title", QuotedFlagValue_Cmd.titleReceived);
        assertEquals("Message", QuotedFlagValue_Cmd.restReceived, "'Message' was NOT part of the flag value - it remains a positional argument");
    }

    // ------------------------------------------------------------------
    // isFlagValue(): a custom parser sees true when parsing a flag's value, false when parsing
    // a positional's value, through the SAME ParseCall it gets either way
    // ------------------------------------------------------------------

    public static class IsFlagTrackingParser extends ArgParser<String> {
        static Boolean lastIsFlag;

        public IsFlagTrackingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            lastIsFlag = call.isFlagValue();
            return ParseResult.of(call.getArgumento().toString());
        }
    }

    @FinalCMD(aliases = "isflagvalueflag")
    public static class IsFlagValueOnFlag_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--tag", parser = IsFlagTrackingParser.class) String tag) {}
    }

    @FinalCMD(aliases = "isflagvaluepos")
    public static class IsFlagValueOnPositional_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[tag]", parser = IsFlagTrackingParser.class) String tag) {}
    }

    @Test
    void isFlagIsTrueWhenParsingAFlagsValue() {
        FinalCMDPluginCommand command = newHarness().register(new IsFlagValueOnFlag_Cmd());
        IsFlagTrackingParser.lastIsFlag = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --tag hello");

        assertEquals(Boolean.TRUE, IsFlagTrackingParser.lastIsFlag);
    }

    @Test
    void isFlagIsFalseWhenParsingAPositionalsValue() {
        FinalCMDPluginCommand command = newHarness().register(new IsFlagValueOnPositional_Cmd());
        IsFlagTrackingParser.lastIsFlag = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub hello");

        assertEquals(Boolean.FALSE, IsFlagTrackingParser.lastIsFlag);
    }

    // ------------------------------------------------------------------
    // a MultiArgumentos contextual parameter on a method with @Arg+@Arg.Flag sees only the
    // post-strip positionals (the flag tokens are already gone)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagmultiargs")
    public static class MultiArgumentosAfterStrip_Cmd {
        static List<String> seenPositionals;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("[value]") String value, @Arg.Flag("--force") Boolean force, MultiArgumentos args) {
            seenPositionals = args.getStringArgs();
            forceReceived = force;
        }
    }

    @Test
    void multiArgumentosContextualSeesOnlyThePostStripPositionals() {
        FinalCMDPluginCommand command = newHarness().register(new MultiArgumentosAfterStrip_Cmd());
        MultiArgumentosAfterStrip_Cmd.seenPositionals = null;
        MultiArgumentosAfterStrip_Cmd.forceReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub hello --force");

        //the window starts AFTER the path, so the subcommand's own label is not one of its positionals
        assertEquals(List.of("hello"), MultiArgumentosAfterStrip_Cmd.seenPositionals);
        assertEquals(Boolean.TRUE, MultiArgumentosAfterStrip_Cmd.forceReceived);
    }

    // ------------------------------------------------------------------
    // two @Arg.Flag on the same method resolve independently of declaration order on the
    // command line
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagtwo")
    public static class TwoIndependentFlags_Cmd {
        static Boolean alphaReceived;
        static Boolean betaReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--alpha") Boolean alpha, @Arg.Flag("--beta") Boolean beta) {
            alphaReceived = alpha;
            betaReceived = beta;
        }
    }

    @Test
    void bothFlagsInDeclarationOrder() {
        FinalCMDPluginCommand command = newHarness().register(new TwoIndependentFlags_Cmd());
        TwoIndependentFlags_Cmd.alphaReceived = null;
        TwoIndependentFlags_Cmd.betaReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --alpha --beta");

        assertEquals(Boolean.TRUE, TwoIndependentFlags_Cmd.alphaReceived);
        assertEquals(Boolean.TRUE, TwoIndependentFlags_Cmd.betaReceived);
    }

    @Test
    void bothFlagsInReverseOrder() {
        FinalCMDPluginCommand command = newHarness().register(new TwoIndependentFlags_Cmd());
        TwoIndependentFlags_Cmd.alphaReceived = null;
        TwoIndependentFlags_Cmd.betaReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --beta --alpha");

        assertEquals(Boolean.TRUE, TwoIndependentFlags_Cmd.alphaReceived);
        assertEquals(Boolean.TRUE, TwoIndependentFlags_Cmd.betaReceived);
    }

    @Test
    void onlyOneOfTwoFlagsPresentLeavesTheOtherNull() {
        FinalCMDPluginCommand command = newHarness().register(new TwoIndependentFlags_Cmd());
        TwoIndependentFlags_Cmd.alphaReceived = Boolean.TRUE; //sentinel
        TwoIndependentFlags_Cmd.betaReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --beta");

        assertNull(TwoIndependentFlags_Cmd.alphaReceived);
        assertEquals(Boolean.TRUE, TwoIndependentFlags_Cmd.betaReceived);
    }

    // ------------------------------------------------------------------
    // array arithmetic: a @Arg.Flag declared BETWEEN two @Arg in the METHOD signature must not
    // disturb theArgs[] slot assignment for either positional
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagbetweenpos")
    public static class FlagBetweenPositionals_Cmd {
        static String beforeReceived;
        static String afterReceived;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<before>") String before, @Arg.Flag("--force") Boolean force, @Arg("<after>") String after) {
            beforeReceived = before;
            forceReceived = force;
            afterReceived = after;
        }
    }

    @Test
    void flagDeclaredBetweenTwoPositionalsInTheMethodSignatureDoesNotDisturbTheArgsSlots() {
        FinalCMDPluginCommand command = newHarness().register(new FlagBetweenPositionals_Cmd());
        FlagBetweenPositionals_Cmd.beforeReceived = null;
        FlagBetweenPositionals_Cmd.afterReceived = null;
        FlagBetweenPositionals_Cmd.forceReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub A B --force");

        assertEquals("A", FlagBetweenPositionals_Cmd.beforeReceived);
        assertEquals("B", FlagBetweenPositionals_Cmd.afterReceived);
        assertEquals(Boolean.TRUE, FlagBetweenPositionals_Cmd.forceReceived);
    }

    // ------------------------------------------------------------------
    // a flag value nobody could read never aborts in silence: the answer is the command's own
    // usage line, exactly like a positional's
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagunreadable")
    public static class UnreadableFlagValue_Cmd {
        static boolean ran;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--title") String title) {
            ran = true;
        }
    }

    @Test
    void emptyFlagValueAnswersWithTheUsageLineInsteadOfAbortingInSilence() {
        FinalCMDPluginCommand command = newHarness().register(new UnreadableFlagValue_Cmd());
        UnreadableFlagValue_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --title ''");

        assertFalse(UnreadableFlagValue_Cmd.ran, "a flag value the parser could not read stops the command");
        assertFalse(sender.getMessages().isEmpty(), "and the sender is told, instead of nothing happening at all");
    }

    // ------------------------------------------------------------------
    // a def() the flag's own parser cannot read is the command's bug, not the sender's: the
    // text nobody typed never shows up in the refusal
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagdefbroken")
    public static class BrokenFlagDefault_Cmd {
        static boolean ran;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--page", def = "primeira") Integer page) {
            ran = true;
        }
    }

    @Test
    void brokenFlagDefaultBlamesTheCommandInsteadOfTheSender() {
        FinalCMDPluginCommand command = newHarness().register(new BrokenFlagDefault_Cmd());
        BrokenFlagDefault_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub");

        assertFalse(BrokenFlagDefault_Cmd.ran);
        assertFalse(sender.anyMessageContains("primeira"), "the sender never typed the def() text");
        assertTrue(sender.anyMessageContains("--page"), "the refusal names the flag whose default is broken");
    }

    // ------------------------------------------------------------------
    // a declared value flag with nothing to take refuses by name: it never degrades to
    // presence and never hands its parser a token nobody typed
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagnovalue")
    public static class ValueFlagWithNothingToTake_Cmd {
        static boolean ran;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--page") Integer page, @Arg.Flag("--force") Boolean force) {
            ran = true;
        }
    }

    @Test
    void valueFlagAtTheEndOfTheLineRefusesInsteadOfBecomingTrue() {
        FinalCMDPluginCommand command = newHarness().register(new ValueFlagWithNothingToTake_Cmd());
        ValueFlagWithNothingToTake_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --page");

        assertFalse(ValueFlagWithNothingToTake_Cmd.ran);
        assertTrue(sender.anyMessageContains("--page"), "the refusal names the flag, not the value it never got");
    }

    @Test
    void valueFlagFollowedByAnotherFlagRefusesInsteadOfEatingIt() {
        FinalCMDPluginCommand command = newHarness().register(new ValueFlagWithNothingToTake_Cmd());
        ValueFlagWithNothingToTake_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --page --force");

        assertFalse(ValueFlagWithNothingToTake_Cmd.ran);
        assertTrue(sender.anyMessageContains("--page"), "the refusal names the flag left without a value");
    }

    // ------------------------------------------------------------------
    // "--name=value" is the same flag as "--name value", quoted groups included; an "=" with
    // nothing after it is a value nobody typed, and a presence flag takes no value at all
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flaginline")
    public static class InlineEqualsFlagValue_Cmd {
        static boolean ran;
        static Integer pageReceived;
        static String titleReceived;
        static Boolean forceReceived;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                        @Arg.Flag("--page") Integer page,
                        @Arg.Flag("--title") String title,
                        @Arg.Flag("--force") Boolean force) {
            ran = true;
            pageReceived = page;
            titleReceived = title;
            forceReceived = force;
        }
    }

    @Test
    void inlineEqualsValueResolvesLikeASeparateToken() {
        FinalCMDPluginCommand command = newHarness().register(new InlineEqualsFlagValue_Cmd());
        InlineEqualsFlagValue_Cmd.pageReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --page=3");

        assertEquals(Integer.valueOf(3), InlineEqualsFlagValue_Cmd.pageReceived);
    }

    @Test
    void inlineEqualsValueGroupsQuotedText() {
        FinalCMDPluginCommand command = newHarness().register(new InlineEqualsFlagValue_Cmd());
        InlineEqualsFlagValue_Cmd.titleReceived = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub --title='Title Message'");

        assertEquals("Title Message", InlineEqualsFlagValue_Cmd.titleReceived);
    }

    @Test
    void inlineEqualsWithNothingAfterItRefuses() {
        FinalCMDPluginCommand command = newHarness().register(new InlineEqualsFlagValue_Cmd());
        InlineEqualsFlagValue_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --page=");

        assertFalse(InlineEqualsFlagValue_Cmd.ran);
        assertFalse(sender.anyMessageContains("--page="), "the flag is known - what is missing is its value");
    }

    @Test
    void inlineEqualsOnAPresenceFlagRefuses() {
        FinalCMDPluginCommand command = newHarness().register(new InlineEqualsFlagValue_Cmd());
        InlineEqualsFlagValue_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "sub --force=false");

        assertFalse(InlineEqualsFlagValue_Cmd.ran);
        assertTrue(sender.anyMessageContains("--force"), "the refusal names the flag");
        assertFalse(sender.anyMessageContains("--force=false"), "the flag is declared - what it does not take is a value");
    }

    // ------------------------------------------------------------------
    // a flag holds one value, so writing it twice is two answers to one question. Neither is
    // taken: picking silently is how "--page 1 --page 2" used to quietly mean page one
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "flagrepeated")
    public static class RepeatedFlag_Cmd {
        static Integer pageReceived;
        static boolean ran;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag(value = "--page", aliases = "-p") Integer page) {
            pageReceived = page;
            ran = true;
        }
    }

    @Test
    void aFlagWrittenTwiceIsRefusedByName() {
        FinalCMDPluginCommand command = newHarness().register(new RepeatedFlag_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        RepeatedFlag_Cmd.ran = false;
        RepeatedFlag_Cmd.pageReceived = null;

        harness.dispatch(command, sender, "sub --page 1 --page 2");

        assertFalse(RepeatedFlag_Cmd.ran, "neither value is taken");
        assertNull(RepeatedFlag_Cmd.pageReceived);
        sender.assertAnyMessageContains("--page");
        sender.assertAnyMessageContains("twice");
    }

    @Test
    void aSecondSpellingOfTheSameFlagCountsAsTheSameFlag() {
        FinalCMDPluginCommand command = newHarness().register(new RepeatedFlag_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        RepeatedFlag_Cmd.ran = false;

        //the alias resolves to the same binding, so "-p" after "--page" is the same question asked twice
        harness.dispatch(command, sender, "sub --page 1 -p 2");

        assertFalse(RepeatedFlag_Cmd.ran);
        sender.assertAnyMessageContains("-p");
    }

    @Test
    void aFlagWrittenOnceIsStillJustAFlag() {
        FinalCMDPluginCommand command = newHarness().register(new RepeatedFlag_Cmd());
        RepeatedFlag_Cmd.ran = false;

        harness.dispatch(command, new TestCommandSender("console"), "sub --page 2");

        assertTrue(RepeatedFlag_Cmd.ran);
        assertEquals(Integer.valueOf(2), RepeatedFlag_Cmd.pageReceived);
    }
}
