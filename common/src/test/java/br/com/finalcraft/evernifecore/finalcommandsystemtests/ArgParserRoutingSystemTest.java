package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestLocaleMessage;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the parser's methods the framework calls, line of the routing table by line of the routing
 * table. The parser records the call instead of deciding anything, because deciding is exactly what it
 * no longer does.
 */
class ArgParserRoutingSystemTest {

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
        harness = new FinalCmdTestHarness("Routing", tempDir);
        TrackingParser.reset();
        return harness;
    }

    // ------------------------------------------------------------------
    // A parser that only tells on itself
    // ------------------------------------------------------------------

    public static class TrackingParser extends ArgParser<String> {
        static final List<String> calls = new ArrayList<>();
        static Boolean lastWasFlagValue;
        /** The token this parser refuses to recognize, so a test can drive parse() to a miss on demand. */
        static final String UNKNOWN = "unknown";
        /** What the refusal says, so a test can tell "reported" from "silently absorbed". */
        static final String REFUSED_TEXT = "TrackingParser does not know that one";

        static void reset() {
            calls.clear();
            lastWasFlagValue = null;
        }

        public TrackingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            calls.add("parse(" + call.getArgumento() + ")");
            lastWasFlagValue = call.isFlagValue();

            if (UNKNOWN.equals(call.getArgumento().toString())){
                calls.add("unrecognized(" + call.getArgumento() + ")");
                return unrecognized(new TestLocaleMessage(REFUSED_TEXT));
            }

            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public ParseResult<String> absent(@Nonnull ParseCall call) {
            calls.add("absent");
            return ParseResult.of("ABSENT");
        }

        @Override
        public ParseResult<String> fromSender(@Nonnull ParseCall call) {
            calls.add("fromSender");
            return ParseResult.of("SENDER:" + call.getSender().getName());
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            calls.add("tabComplete");
            return Collections.singletonList("tabbed");
        }
    }

    /** Reads something off the sender that a console simply does not have - like a location. */
    public static class SenderlessParser extends ArgParser<String> {
        static final String NOTHING_TO_INFER = "there is nothing to read off this sender";

        public SenderlessParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public ParseResult<String> fromSender(@Nonnull ParseCall call) {
            return unrecognized(new TestLocaleMessage(NOTHING_TO_INFER));
        }
    }

    /** A parser whose dependency is down - the failure nobody's typing can avoid. */
    public static class ExplodingParser extends ArgParser<String> {
        static final String BOOM = "the backend this parser needs is down";

        public ExplodingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            throw new IllegalStateException(BOOM);
        }
    }

    @FinalCMD(aliases = "routingcmd")
    public static class Routing_Cmd {
        static String required;
        static String optional;
        static String withDef;
        static String inferred;
        static String flagged;
        static boolean invoked;

        @FinalCMD.SubCMD(subcmd = "req")
        public void req(FCommandSender sender, @Arg(value = "<value>", parser = TrackingParser.class) String value) {
            invoked = true;
            required = value;
        }

        @FinalCMD.SubCMD(subcmd = "opt")
        public void opt(FCommandSender sender, @Arg(value = "[value]", parser = TrackingParser.class) String value) {
            invoked = true;
            optional = value;
        }

        @FinalCMD.SubCMD(subcmd = "def")
        public void def(FCommandSender sender, @Arg(value = "[value]", def = "fallback", parser = TrackingParser.class) String value) {
            invoked = true;
            withDef = value;
        }

        @FinalCMD.SubCMD(subcmd = "sender")
        public void sender(FCommandSender sender,
                           @Arg("<other>") String other,
                           @Arg(value = "<value>", fromSender = true, parser = TrackingParser.class) String value) {
            invoked = true;
            inferred = value;
        }

        @FinalCMD.SubCMD(subcmd = "flag")
        public void flag(FCommandSender sender, @Arg.Flag(value = "--tag", parser = TrackingParser.class) String tag) {
            invoked = true;
            flagged = tag;
        }

        @FinalCMD.SubCMD(subcmd = "baddef")
        public void baddef(FCommandSender sender,
                           @Arg(value = "[value]", def = TrackingParser.UNKNOWN, parser = TrackingParser.class) String value) {
            invoked = true;
            withDef = value;
        }

        @FinalCMD.SubCMD(subcmd = "senderless")
        public void senderless(FCommandSender sender,
                               @Arg(value = "<value>", fromSender = true, parser = SenderlessParser.class) String value) {
            invoked = true;
            inferred = value;
        }

        @FinalCMD.SubCMD(subcmd = "boom")
        public void boom(FCommandSender sender,
                         @Arg(value = "<value>", parser = ExplodingParser.class) String value) {
            invoked = true;
        }
    }

    private FinalCMDPluginCommand fresh() {
        FinalCMDPluginCommand command = newHarness().register(new Routing_Cmd());
        Routing_Cmd.invoked = false;
        return command;
    }

    // ------------------------------------------------------------------
    // Token present
    // ------------------------------------------------------------------

    @Test
    void aTokenThatConvertsOnlyEverReachesParse() {
        FinalCMDPluginCommand command = fresh();

        harness.dispatch(command, new TestCommandSender("console"), "req hello");

        assertEquals(Arrays.asList("parse(hello)"), TrackingParser.calls);
        assertEquals("hello", Routing_Cmd.required);
        assertEquals(Boolean.FALSE, TrackingParser.lastWasFlagValue);
    }

    @Test
    void aTokenTheParserDoesNotRecognizeIsRejectedOnlyWhenTheArgumentIsRequired() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "req " + TrackingParser.UNKNOWN);

        assertEquals(Arrays.asList("parse(unknown)", "unrecognized(unknown)"), TrackingParser.calls);
        assertFalse(Routing_Cmd.invoked, "a rejected required argument stops the command");
        sender.assertAnyMessageContains(TrackingParser.REFUSED_TEXT);

        TrackingParser.reset();
        sender.clearMessages();
        Routing_Cmd.optional = "sentinel";
        harness.dispatch(command, sender, "opt " + TrackingParser.UNKNOWN);

        assertEquals(Arrays.asList("parse(unknown)", "unrecognized(unknown)"), TrackingParser.calls);
        assertTrue(Routing_Cmd.invoked);
        assertNull(Routing_Cmd.optional);
        sender.assertNoMessageSent();
    }

    // ------------------------------------------------------------------
    // No token at all
    // ------------------------------------------------------------------

    @Test
    void aMissingRequiredTokenSendsTheHelpLineWithoutAskingTheParserAnything() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "req");

        assertTrue(TrackingParser.calls.isEmpty(), "the parser has nothing to say about a token that was not typed");
        assertFalse(Routing_Cmd.invoked);
        assertFalse(sender.getMessages().isEmpty(), "the help line is what answers");
    }

    @Test
    void aMissingOptionalTokenGoesToAbsentUnlessThereIsADef() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "opt");
        assertEquals(Arrays.asList("absent"), TrackingParser.calls);
        assertEquals("ABSENT", Routing_Cmd.optional);

        TrackingParser.reset();
        harness.dispatch(command, sender, "def");
        assertEquals(Arrays.asList("parse(fallback)"), TrackingParser.calls, "a def() is parsed as if it had been typed");
        assertEquals("fallback", Routing_Cmd.withDef);
    }

    @Test
    void aMissingTokenOnAFromSenderArgumentGoesToFromSender() {
        FinalCMDPluginCommand command = fresh();

        harness.dispatch(command, new TestCommandSender("Admin"), "sender other");

        assertEquals(Arrays.asList("fromSender"), TrackingParser.calls);
        assertEquals("SENDER:Admin", Routing_Cmd.inferred);

        TrackingParser.reset();
        harness.dispatch(command, new TestCommandSender("Admin"), "sender other named");
        assertEquals(Arrays.asList("parse(named)"), TrackingParser.calls, "a token that WAS typed is never inferred");
        assertEquals("named", Routing_Cmd.inferred);
    }

    // ------------------------------------------------------------------
    // A flag's value, and the tab
    // ------------------------------------------------------------------

    @Test
    void aFlagsValueGoesThroughParseAndSaysSo() {
        FinalCMDPluginCommand command = fresh();

        harness.dispatch(command, new TestCommandSender("console"), "flag --tag hello");

        assertEquals(Arrays.asList("parse(hello)"), TrackingParser.calls);
        assertEquals(Boolean.TRUE, TrackingParser.lastWasFlagValue);
        assertEquals("hello", Routing_Cmd.flagged);
    }

    @Test
    void aFlagValueTheParserDoesNotRecognizeIsRejected() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "flag --tag " + TrackingParser.UNKNOWN);

        assertEquals(Arrays.asList("parse(unknown)", "unrecognized(unknown)"), TrackingParser.calls);
        assertFalse(Routing_Cmd.invoked);
        sender.assertAnyMessageContains(TrackingParser.REFUSED_TEXT);
    }

    // ------------------------------------------------------------------
    // What the engine changed: three failures that used to pass silently
    // ------------------------------------------------------------------

    @Test
    void aFromSenderThatResolvesNothingOnARequiredArgumentAbortsInsteadOfPassingNull() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");
        Routing_Cmd.inferred = "sentinel";

        harness.dispatch(command, sender, "senderless");

        assertFalse(Routing_Cmd.invoked, "a required argument nobody could infer stops the command");
        assertEquals("sentinel", Routing_Cmd.inferred, "the method never ran, so it never got a null");
        sender.assertAnyMessageContains(SenderlessParser.NOTHING_TO_INFER);
    }

    @Test
    void aDefThatTheParserDoesNotRecognizeIsAnInternalErrorNotASilentNull() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");
        Routing_Cmd.withDef = "sentinel";

        harness.dispatch(command, sender, "baddef");

        assertEquals(Arrays.asList("parse(unknown)", "unrecognized(unknown)"), TrackingParser.calls);
        assertFalse(Routing_Cmd.invoked, "a def() the command cannot read is a bug in the command");
        assertEquals("sentinel", Routing_Cmd.withDef);
        assertFalse(sender.getMessages().isEmpty(), "whoever typed it is still told the argument did not work");
    }

    @Test
    void aRuntimeExceptionInsideAParserIsCaughtAndTheSenderIsToldSomethingGeneric() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "boom anything");

        assertFalse(Routing_Cmd.invoked);
        assertFalse(sender.getMessages().isEmpty(), "the exception no longer escapes without a word");
        assertFalse(sender.anyMessageContains(ExplodingParser.BOOM),
                "the sender is told the argument failed, never the internals: " + sender.getMessages());
    }

    @Test
    void tabIsTheOnlyThingThatRunsDuringTabComplete() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(Arrays.asList("tabbed"), harness.tab(command, sender, "req", ""));
        assertEquals(Arrays.asList("tabbed"), harness.tab(command, sender, "flag", "--tag", ""));

        assertEquals(Arrays.asList("tabComplete", "tabComplete"), TrackingParser.calls);
    }
}
