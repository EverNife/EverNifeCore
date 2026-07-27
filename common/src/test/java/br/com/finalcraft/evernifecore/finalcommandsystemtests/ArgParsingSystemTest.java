package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
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
 * Pins {@code @Arg} parsing and the builtin {@link ArgParser}s (matrix C): required/optional
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
    // C1 - a missing <required> arg sends the help line and does not invoke the method
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c1cmd")
    public static class C1_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") String value) {
            invoked = true;
        }
    }

    @Test
    void c1_missingRequiredArgSendsHelpLineAndSkipsInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new C1_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        C1_Cmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(C1_Cmd.invoked);
        assertFalse(sender.getMessages().isEmpty(), "the help line should have been sent");
    }

    // ------------------------------------------------------------------
    // C2 - a missing [optional] arg is null, the method still runs
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c2cmd")
    public static class C2_Cmd {
        static boolean invoked = false;
        static String received = "not-called";

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "[value]") String value) {
            invoked = true;
            received = value;
        }
    }

    @Test
    void c2_missingOptionalArgIsNullAndMethodRuns() {
        FinalCMDPluginCommand command = newHarness().register(new C2_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        C2_Cmd.invoked = false;
        C2_Cmd.received = "not-called";

        harness.dispatch(command, sender, "sub");

        assertTrue(C2_Cmd.invoked);
        assertNull(C2_Cmd.received);
    }

    // ------------------------------------------------------------------
    // C3 - happy path for every builtin type (1 test per type)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c3string")
    public static class C3_StringCmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") String value) {
            received = value;
        }
    }

    @Test
    void c3_string_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_StringCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub hello");

        assertEquals("hello", C3_StringCmd.received);
    }

    @FinalCMD(aliases = "c3integer")
    public static class C3_IntegerCmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Integer value) {
            received = value;
        }
    }

    @Test
    void c3_integer_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_IntegerCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 42");

        assertEquals(42, C3_IntegerCmd.received);
    }

    @FinalCMD(aliases = "c3double")
    public static class C3_DoubleCmd {
        static Double received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Double value) {
            received = value;
        }
    }

    @Test
    void c3_double_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_DoubleCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 3.5");

        assertEquals(3.5, C3_DoubleCmd.received);
    }

    @FinalCMD(aliases = "c3boolean")
    public static class C3_BooleanCmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Boolean value) {
            received = value;
        }
    }

    @Test
    void c3_boolean_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_BooleanCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub true");

        assertEquals(Boolean.TRUE, C3_BooleanCmd.received);
    }

    public enum SampleEnum {ALPHA, BETA}

    @FinalCMD(aliases = "c3enum")
    public static class C3_EnumCmd {
        static SampleEnum received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") SampleEnum value) {
            received = value;
        }
    }

    @Test
    void c3_enum_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_EnumCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub beta");

        assertEquals(SampleEnum.BETA, C3_EnumCmd.received);
    }

    @FinalCMD(aliases = "c3timeframe")
    public static class C3_TimeFrameCmd {
        static FCTimeFrame received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") FCTimeFrame value) {
            received = value;
        }
    }

    @Test
    void c3_fcTimeFrame_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_TimeFrameCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 2h30m");

        assertNotNull(C3_TimeFrameCmd.received);
        assertEquals(2 * 60 * 60 * 1000L + 30 * 60 * 1000L, C3_TimeFrameCmd.received.getMillis());
    }

    @FinalCMD(aliases = "c3numberwrapper")
    public static class C3_NumberWrapperCmd {
        static NumberWrapper received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") NumberWrapper value) {
            received = value;
        }
    }

    @Test
    void c3_numberWrapper_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_NumberWrapperCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub 7.25");

        assertNotNull(C3_NumberWrapperCmd.received);
        assertEquals(7.25, C3_NumberWrapperCmd.received.doubleValue());
    }

    @FinalCMD(aliases = "c3argumento")
    public static class C3_ArgumentoCmd {
        static Argumento received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Argumento value) {
            received = value;
        }
    }

    @Test
    void c3_argumento_happyPath() {
        FinalCMDPluginCommand command = newHarness().register(new C3_ArgumentoCmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub rawtoken");

        assertNotNull(C3_ArgumentoCmd.received);
        assertEquals("rawtoken", C3_ArgumentoCmd.received.toString());
    }

    @FinalCMD(aliases = "c3uuid")
    public static class C3_UUIDCmd {
        static UUID received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") UUID value) {
            received = value;
        }
    }

    @Test
    void c3_uuid_happyPath() throws IOException {
        //ArgParserUUID.parserArgument requires PlayerController.getLoaded(uuid) != null - the only
        //builtin type in this matrix row that needs live PlayerController/storage state (D3); an H2
        //in-memory backend keeps it headless and fast.
        UUID uuid = UUID.randomUUID();
        try {
            PlayerController.initialize(Storages.h2("c3uuid").writeTo(tempDir));
            PlayerController.handleLogin(uuid, "C3UuidPlayer").join();
            assertNotNull(PlayerController.getLoaded(uuid), "fixture setup: the player should be loaded");

            FinalCMDPluginCommand command = newHarness().register(new C3_UUIDCmd());
            C3_UUIDCmd.received = null;
            harness.dispatch(command, new TestCommandSender("console"), "sub " + uuid);

            assertEquals(uuid, C3_UUIDCmd.received);
        } finally {
            PlayerController.shutdown();
            PlayerController.getConfiguredPDSections().clear();
        }
    }


    // ------------------------------------------------------------------
    // C4 - an invalid Integer sends the parser's error message, does not invoke, does not throw
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c4cmd")
    public static class C4_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") Integer value) {
            invoked = true;
        }
    }

    @Test
    void c4_invalidIntegerSendsParserErrorAndSkipsInvocationWithoutThrowing() {
        FinalCMDPluginCommand command = newHarness().register(new C4_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        C4_Cmd.invoked = false;

        harness.dispatch(command, sender, "sub notanumber");

        assertFalse(C4_Cmd.invoked);
        sender.assertAnyMessageContains("needs to be an integer");
    }

    // ------------------------------------------------------------------
    // C5 - context = "[1:*]" on Integer: below the bound errors, within it is fine
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c5cmd")
    public static class C5_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>", context = "[1:*]") Integer value) {
            received = value;
        }
    }

    @Test
    void c5_belowLowerBoundErrors() {
        FinalCMDPluginCommand command = newHarness().register(new C5_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        C5_Cmd.received = null;

        harness.dispatch(command, sender, "sub 0");

        assertNull(C5_Cmd.received);
        sender.assertAnyMessageContains("higher than");
    }

    @Test
    void c5_withinBoundIsAccepted() {
        FinalCMDPluginCommand command = newHarness().register(new C5_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        C5_Cmd.received = null;

        harness.dispatch(command, sender, "sub 5");

        assertEquals(5, C5_Cmd.received);
    }

    // ------------------------------------------------------------------
    // C6 - context = "a|b|c" on String/Enum: outside the list errors
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c6string")
    public static class C6_StringCmd {
        static String received = "not-called";

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>", context = "a|b|c") String value) {
            received = value;
        }
    }

    @Test
    void c6_string_outsideTheListErrors() {
        FinalCMDPluginCommand command = newHarness().register(new C6_StringCmd());
        TestCommandSender sender = new TestCommandSender("console");
        C6_StringCmd.received = "not-called";

        harness.dispatch(command, sender, "sub z");

        assertEquals("not-called", C6_StringCmd.received);
        sender.assertAnyMessageContains("must be");
    }

    @Test
    void c6_string_insideTheListIsAccepted() {
        FinalCMDPluginCommand command = newHarness().register(new C6_StringCmd());
        TestCommandSender sender = new TestCommandSender("console");
        C6_StringCmd.received = "not-called";

        //ArgsParserUtil.parseStringContextSelectional lower-cases every option: the returned value is
        //always lowercase, regardless of the casing the option list or the input used
        harness.dispatch(command, sender, "sub B");

        assertEquals("b", C6_StringCmd.received);
    }

    @FinalCMD(aliases = "c6enum")
    public static class C6_EnumCmd {
        static SampleEnum received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>", context = "ALPHA") SampleEnum value) {
            received = value;
        }
    }

    @Test
    void c6_enum_outsideTheRestrictedContextErrors() {
        FinalCMDPluginCommand command = newHarness().register(new C6_EnumCmd());
        TestCommandSender sender = new TestCommandSender("console");
        C6_EnumCmd.received = null;

        //BETA exists on the enum but was not included in the @Arg context, so it's outside the list
        harness.dispatch(command, sender, "sub beta");

        assertNull(C6_EnumCmd.received);
        sender.assertAnyMessageContains("must be");
    }

    // ------------------------------------------------------------------
    // C7 - a custom @Arg(parser = ...) is respected
    // ------------------------------------------------------------------

    public static class ReverseStringParser extends ArgParser<String> {
        public ReverseStringParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public String parserArgument(ArgParserCommandContext argContext, FCommandSender sender, Argumento argumento) throws ArgParseException {
            return new StringBuilder(argumento.toString()).reverse().toString();
        }
    }

    @FinalCMD(aliases = "c7cmd")
    public static class C7_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>", parser = ReverseStringParser.class) String value) {
            received = value;
        }
    }

    @Test
    void c7_customArgParserIsRespected() {
        FinalCMDPluginCommand command = newHarness().register(new C7_Cmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub hello");

        assertEquals("olleh", C7_Cmd.received);
    }

    // ------------------------------------------------------------------
    // C8 - ArgRequirementType brackets: <x>, [x], <(x)>, [(x)] recognized; no brackets fails
    // registration (ArgMountException, swallowed to a plain false - see RegistrationSystemTest A6/A10)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c8cmd")
    public static class C8_AllBracketsCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender,
                         @Arg(name = "<required>") String required,
                         @Arg(name = "[optional]") String optional,
                         @Arg(name = "<(providedRequired)>") String providedRequired,
                         @Arg(name = "[(providedOptional)]") String providedOptional) {
        }
    }

    @Test
    void c8_allFourBracketFormsAreRecognizedAtRegistration() {
        FinalCMDPluginCommand command = newHarness().register(new C8_AllBracketsCmd());

        assertNotNull(command, "all four ArgRequirementType bracket forms should register cleanly");
    }

    public static class C8_NoBracketsCmd {
        @FinalCMD(aliases = "c8nobrackets")
        public void run(FCommandSender sender, @Arg(name = "novalidbrackets") String value) {}
    }

    @Test
    void c8_nameWithoutBracketsFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new C8_NoBracketsCmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // C9 - a sub-command's first @Arg reads args[1] (args[0] is the sub-command's own name)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c9cmd")
    public static class C9_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<value>") String value) {
            received = value;
        }
    }

    @Test
    void c9_firstArgOfASubCommandReadsIndexOneNotZero() {
        FinalCMDPluginCommand command = newHarness().register(new C9_Cmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub theValue");

        //"sub" occupies args[0]; the @Arg reads args[1], not args[0]
        assertEquals("theValue", C9_Cmd.received);
    }

    // ------------------------------------------------------------------
    // C10 - two @Arg of the same type: both get parsed, in order, and both reach the method
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c10cmd")
    public static class C10_Cmd {
        static String first;
        static String second;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(name = "<first>") String first, @Arg(name = "<second>") String second) {
            C10_Cmd.first = first;
            C10_Cmd.second = second;
        }
    }

    @Test
    void c10_twoArgsOfTheSameTypeBothReachTheMethodInOrder() {
        FinalCMDPluginCommand command = newHarness().register(new C10_Cmd());
        harness.dispatch(command, new TestCommandSender("console"), "sub foo bar");

        assertEquals("foo", C10_Cmd.first);
        assertEquals("bar", C10_Cmd.second);
    }
}
