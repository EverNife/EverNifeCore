package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code @Arg} parsing and the builtin {@link ArgParser}s: required/optional
 * handling, the happy path of every builtin type, error messaging, context bounds/selection,
 * custom parsers, {@link br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType},
 * sub-command positional offsets, and repeated-type args.
 */
@ECoreTest
class ArgParsingSystemTest {

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
        harness = new FinalCmdTestHarness("ArgParsing", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // a missing <required> arg sends the help line and does not invoke the method
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "missingrequired")
    public static class MissingRequiredArgCmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") String value) {
            invoked = true;
        }
    }

    @Test
    void missingRequiredArgSendsHelpLineAndSkipsInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new MissingRequiredArgCmd());
        TestCommandSender sender = new TestCommandSender("console");
        MissingRequiredArgCmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(MissingRequiredArgCmd.invoked);
        assertFalse(sender.getMessages().isEmpty(), "the help line should have been sent");
    }

    // ------------------------------------------------------------------
    // a missing [optional] arg is null, the method still runs
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "missingoptional")
    public static class MissingOptionalArgCmd {
        static boolean invoked = false;
        static String received = "not-called";

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("[value]") String value) {
            invoked = true;
            received = value;
        }
    }

    @Test
    void missingOptionalArgIsNullAndMethodRuns() {
        FinalCMDPluginCommand command = newHarness().register(new MissingOptionalArgCmd());
        TestCommandSender sender = new TestCommandSender("console");
        MissingOptionalArgCmd.invoked = false;
        MissingOptionalArgCmd.received = "not-called";

        harness.dispatch(command, sender, "sub");

        assertTrue(MissingOptionalArgCmd.invoked);
        assertNull(MissingOptionalArgCmd.received);
    }

    // ------------------------------------------------------------------
    // happy path for every builtin type (1 test per type)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "typestring")
    public static class StringArgCmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") String value) {
            received = value;
        }
    }

    @Test
    void string_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new StringArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub hello");

        assertEquals("hello", StringArgCmd.received);
    }

    @FinalCMD(aliases = "typeinteger")
    public static class IntegerArgCmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") Integer value) {
            received = value;
        }
    }

    @Test
    void integer_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new IntegerArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 42");

        assertEquals(42, IntegerArgCmd.received);
    }

    @FinalCMD(aliases = "typedouble")
    public static class DoubleArgCmd {
        static Double received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") Double value) {
            received = value;
        }
    }

    @Test
    void double_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new DoubleArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 3.5");

        assertEquals(3.5, DoubleArgCmd.received);
    }

    @FinalCMD(aliases = "typeboolean")
    public static class BooleanArgCmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") Boolean value) {
            received = value;
        }
    }

    @Test
    void boolean_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new BooleanArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub true");

        assertEquals(Boolean.TRUE, BooleanArgCmd.received);
    }

    public enum SampleEnum {ALPHA, BETA}

    @FinalCMD(aliases = "typeenum")
    public static class EnumArgCmd {
        static SampleEnum received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") SampleEnum value) {
            received = value;
        }
    }

    @Test
    void enum_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new EnumArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub beta");

        assertEquals(SampleEnum.BETA, EnumArgCmd.received);
    }

    @FinalCMD(aliases = "typetimeframe")
    public static class TimeFrameArgCmd {
        static FCTimeFrame received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") FCTimeFrame value) {
            received = value;
        }
    }

    @Test
    void fcTimeFrame_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new TimeFrameArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 2h30m");

        assertNotNull(TimeFrameArgCmd.received);
        assertEquals(2 * 60 * 60 * 1000L + 30 * 60 * 1000L, TimeFrameArgCmd.received.getMillis());
    }

    @FinalCMD(aliases = "typenumberwrapper")
    public static class NumberWrapperArgCmd {
        static NumberWrapper received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") NumberWrapper value) {
            received = value;
        }
    }

    @Test
    void numberWrapper_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new NumberWrapperArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 7.25");

        assertNotNull(NumberWrapperArgCmd.received);
        assertEquals(7.25, NumberWrapperArgCmd.received.doubleValue());
    }

    @FinalCMD(aliases = "typeargumento")
    public static class ArgumentoArgCmd {
        static Argumento received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") Argumento value) {
            received = value;
        }
    }

    @Test
    void argumento_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new ArgumentoArgCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub rawtoken");

        assertNotNull(ArgumentoArgCmd.received);
        assertEquals("rawtoken", ArgumentoArgCmd.received.toString());
    }

    @FinalCMD(aliases = "typeuuid")
    public static class UUIDArgCmd {
        static UUID received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") UUID value) {
            received = value;
        }
    }

    @Test
    void uuid_happyPath() throws IOException {
        //ArgParserUUID.parse requires PlayerController.getLoaded(uuid) != null - the only
        //builtin type here that needs live PlayerController/storage state; an H2
        //in-memory backend keeps it headless and fast.
        UUID uuid = UUID.randomUUID();
        try {
            PlayerController.initialize(Storages.h2("uuidarg").writeTo(tempDir));
            PlayerController.handleLogin(uuid, "UuidArgPlayer").join();
            assertNotNull(PlayerController.getLoaded(uuid), "fixture setup: the player should be loaded");

            FinalCMDPluginCommand command = newHarness().register(new UUIDArgCmd());
            UUIDArgCmd.received = null;
            harness.dispatch(command, new TestCommandSender("console"), "sub " + uuid);

            assertEquals(uuid, UUIDArgCmd.received);
        } finally {
            PlayerController.shutdown();
            PlayerController.getConfiguredPDSections().clear();
        }
    }


    // ------------------------------------------------------------------
    // an invalid Integer sends the parser's error message, does not invoke, does not throw
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "invalidinteger")
    public static class InvalidIntegerArgCmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") Integer value) {
            invoked = true;
        }
    }

    @Test
    void invalidIntegerSendsParserErrorAndSkipsInvocationWithoutThrowing() {
        FinalCMDPluginCommand command = newHarness().register(new InvalidIntegerArgCmd());
        TestCommandSender sender = new TestCommandSender("console");
        InvalidIntegerArgCmd.invoked = false;

        harness.dispatch(command, sender, "sub notanumber");

        assertFalse(InvalidIntegerArgCmd.invoked);
        sender.assertAnyMessageContains("needs to be an integer");
    }

    // ------------------------------------------------------------------
    // context = "[1:*]" on Integer: below the bound errors, within it is fine
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "lowerbound")
    public static class LowerBoundedIntegerCmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "[1:*]") Integer value) {
            received = value;
        }
    }

    @Test
    void belowLowerBoundErrors() {
        FinalCMDPluginCommand command = newHarness().register(new LowerBoundedIntegerCmd());
        TestCommandSender sender = new TestCommandSender("console");
        LowerBoundedIntegerCmd.received = null;

        harness.dispatch(command, sender, "sub 0");

        assertNull(LowerBoundedIntegerCmd.received);
        sender.assertAnyMessageContains("higher than");
    }

    @Test
    void withinBoundIsAccepted() {
        FinalCMDPluginCommand command = newHarness().register(new LowerBoundedIntegerCmd());
        TestCommandSender sender = new TestCommandSender("console");
        LowerBoundedIntegerCmd.received = null;

        harness.dispatch(command, sender, "sub 5");

        assertEquals(5, LowerBoundedIntegerCmd.received);
    }

    // ------------------------------------------------------------------
    // A numeric selection list is declared in decimal, but the argument type decides what the parser
    // produces - so the list has to be compared numerically, never by the boxed type
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "numselectint")
    public static class NumericSelectionalIntegerCmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "1|2|3") Integer value) {
            received = value;
        }
    }

    @Test
    void aNumericSelectionListAcceptsAValueOfItOnAnIntegerArgument() {
        FinalCMDPluginCommand command = newHarness().register(new NumericSelectionalIntegerCmd());
        TestCommandSender sender = new TestCommandSender("console");
        NumericSelectionalIntegerCmd.received = null;

        harness.dispatch(command, sender, "sub 2");

        assertEquals(2, NumericSelectionalIntegerCmd.received);
        assertTrue(sender.getMessages().isEmpty(), "a value that is on the list is not an error");
    }

    @Test
    void aNumericSelectionListRefusesAValueOutsideItOnAnIntegerArgument() {
        FinalCMDPluginCommand command = newHarness().register(new NumericSelectionalIntegerCmd());
        TestCommandSender sender = new TestCommandSender("console");
        NumericSelectionalIntegerCmd.received = null;

        harness.dispatch(command, sender, "sub 7");

        assertNull(NumericSelectionalIntegerCmd.received);
        sender.assertAnyMessageContains("must be");
    }

    @FinalCMD(aliases = "numselectdouble")
    public static class NumericSelectionalDoubleCmd {
        static Double received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "1|2|3") Double value) {
            received = value;
        }
    }

    @Test
    void aNumericSelectionListAcceptsAValueOfItOnADoubleArgument() {
        FinalCMDPluginCommand command = newHarness().register(new NumericSelectionalDoubleCmd());
        TestCommandSender sender = new TestCommandSender("console");
        NumericSelectionalDoubleCmd.received = null;

        harness.dispatch(command, sender, "sub 2");

        assertEquals(2.0, NumericSelectionalDoubleCmd.received);
        assertTrue(sender.getMessages().isEmpty(), "a value that is on the list is not an error");
    }

    @Test
    void aNumericSelectionListRefusesAValueOutsideItOnADoubleArgument() {
        FinalCMDPluginCommand command = newHarness().register(new NumericSelectionalDoubleCmd());
        TestCommandSender sender = new TestCommandSender("console");
        NumericSelectionalDoubleCmd.received = null;

        harness.dispatch(command, sender, "sub 7");

        assertNull(NumericSelectionalDoubleCmd.received);
        sender.assertAnyMessageContains("must be");
    }

    @FinalCMD(aliases = "numintervalint")
    public static class NumericIntervalIntegerCmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "[1:5]") Integer value) {
            received = value;
        }
    }

    @FinalCMD(aliases = "numintervaldouble")
    public static class NumericIntervalDoubleCmd {
        static Double received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "[1:5]") Double value) {
            received = value;
        }
    }

    @Test
    void aNumericIntervalAcceptsAValueInsideItOnAnIntegerArgument() {
        FinalCMDPluginCommand command = newHarness().register(new NumericIntervalIntegerCmd());
        NumericIntervalIntegerCmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub 3");

        assertEquals(3, NumericIntervalIntegerCmd.received);
    }

    @Test
    void aNumericIntervalAcceptsAValueInsideItOnADoubleArgument() {
        FinalCMDPluginCommand command = newHarness().register(new NumericIntervalDoubleCmd());
        NumericIntervalDoubleCmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub 3");

        assertEquals(3.0, NumericIntervalDoubleCmd.received);
    }

    // ------------------------------------------------------------------
    // context = "a|b|c" on String/Enum: outside the list errors
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "selectionstring")
    public static class StringSelectionCmd {
        static String received = "not-called";

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "a|b|c") String value) {
            received = value;
        }
    }

    @Test
    void string_outsideTheListErrors() {
        FinalCMDPluginCommand command = newHarness().register(new StringSelectionCmd());
        TestCommandSender sender = new TestCommandSender("console");
        StringSelectionCmd.received = "not-called";

        harness.dispatch(command, sender, "sub z");

        assertEquals("not-called", StringSelectionCmd.received);
        sender.assertAnyMessageContains("must be");
    }

    @Test
    void string_insideTheListIsAccepted() {
        FinalCMDPluginCommand command = newHarness().register(new StringSelectionCmd());
        TestCommandSender sender = new TestCommandSender("console");
        StringSelectionCmd.received = "not-called";

        //ArgsParserUtil.parseStringContextSelectional lower-cases every option: the returned value is
        //always lowercase, regardless of the casing the option list or the input used
        harness.dispatch(command, sender, "sub B");

        assertEquals("b", StringSelectionCmd.received);
    }

    @FinalCMD(aliases = "selectionenum")
    public static class EnumSelectionCmd {
        static SampleEnum received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", context = "ALPHA") SampleEnum value) {
            received = value;
        }
    }

    @Test
    void enum_outsideTheRestrictedContextErrors() {
        FinalCMDPluginCommand command = newHarness().register(new EnumSelectionCmd());
        TestCommandSender sender = new TestCommandSender("console");
        EnumSelectionCmd.received = null;

        //BETA exists on the enum but was not included in the @Arg context, so it's outside the list
        harness.dispatch(command, sender, "sub beta");

        assertNull(EnumSelectionCmd.received);
        sender.assertAnyMessageContains("must be");
    }

    // ------------------------------------------------------------------
    // a custom @Arg(parser = ...) is respected
    // ------------------------------------------------------------------

    public static class ReverseStringParser extends ArgParser<String> {
        public ReverseStringParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(ParseCall call) {
            return ParseResult.of(new StringBuilder(call.getArgumento().toString()).reverse().toString());
        }
    }

    @FinalCMD(aliases = "customparser")
    public static class CustomParserCmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "<value>", parser = ReverseStringParser.class) String value) {
            received = value;
        }
    }

    @Test
    void customArgParserIsRespected() {
        FinalCMDPluginCommand command = newHarness().register(new CustomParserCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub hello");

        assertEquals("olleh", CustomParserCmd.received);
    }

    // ------------------------------------------------------------------
    // ArgRequirementType brackets: <x> and [x] recognized; no brackets fails registration
    // (ArgMountException, swallowed to a plain false - see RegistrationSystemTest)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "allbrackets")
    public static class AllBracketsCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                         @Arg("<required>") String required,
                         @Arg("[optional]") String optional) {
        }
    }

    @Test
    void bothBracketFormsAreRecognizedAtRegistration() {
        FinalCMDPluginCommand command = newHarness().register(new AllBracketsCmd());

        assertNotNull(command, "both ArgRequirementType bracket forms should register cleanly");
    }

    public static class NoBracketsCmd {
        @FinalCMD(aliases = "nobrackets")
        public void run(FCommandSender sender, @Arg("novalidbrackets") String value) {}
    }

    @Test
    void nameWithoutBracketsFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new NoBracketsCmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // a sub-command's first @Arg reads args[1] (args[0] is the sub-command's own name)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "subargindex")
    public static class SubCommandArgIndexCmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<value>") String value) {
            received = value;
        }
    }

    @Test
    void firstArgOfASubCommandReadsIndexOneNotZero() {
        FinalCMDPluginCommand command = newHarness().register(new SubCommandArgIndexCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub theValue");

        //"sub" occupies args[0]; the @Arg reads args[1], not args[0]
        assertEquals("theValue", SubCommandArgIndexCmd.received);
    }

    // ------------------------------------------------------------------
    // two @Arg of the same type: both get parsed, in order, and both reach the method
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "twosametype")
    public static class TwoArgsSameTypeCmd {
        static String first;
        static String second;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("<first>") String first, @Arg("<second>") String second) {
            TwoArgsSameTypeCmd.first = first;
            TwoArgsSameTypeCmd.second = second;
        }
    }

    @Test
    void twoArgsOfTheSameTypeBothReachTheMethodInOrder() {
        FinalCMDPluginCommand command = newHarness().register(new TwoArgsSameTypeCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub foo bar");

        assertEquals("foo", TwoArgsSameTypeCmd.first);
        assertEquals("bar", TwoArgsSameTypeCmd.second);
    }
}
