package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.AbstractArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.IParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseEngine;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseOutcome;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolvedArguments;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestLocaleMessage;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routing table and the policy matrix, line by line, against parsers that do nothing but answer
 * what the test told them to. No command is registered here: the engine is the unit.
 */
class ParseEngineTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    //The engine's own failure reporting reaches FCMessageUtil and EverNifeCore.getLog(), neither of
    //which exists in a bare JVM
    private FinalCmdTestHarness harness;
    private TestCommandSender sender;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("ParseEngine", tempDir);
        sender = new TestCommandSender("console");
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    // ------------------------------------------------------------------
    // A parser that answers whatever the test handed it, and says who was asked
    // ------------------------------------------------------------------

    private static class ScriptedParser extends ArgParser<String> {

        final List<String> calls = new ArrayList<>();
        Function<ParseCall, ParseResult<String>> onParse = call -> ParseResult.of(call.getArgumento().toString());
        Function<ParseCall, ParseResult<String>> onAbsent = call -> ParseResult.of("ABSENT");
        Function<ParseCall, ParseResult<String>> onFromSender = call -> ParseResult.of("SENDER");

        ScriptedParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            calls.add("parse(" + call.getArgumento() + ")");
            return onParse.apply(call);
        }

        @Override
        public ParseResult<String> absent(@Nonnull ParseCall call) {
            calls.add("absent");
            return onAbsent.apply(call);
        }

        @Override
        public ParseResult<String> fromSender(@Nonnull ParseCall call) {
            calls.add("fromSender");
            return onFromSender.apply(call);
        }
    }

    /** Counts what the engine delivered, without the delivery itself getting in the way. */
    private static class RecordingEngine extends ParseEngine {

        final List<String> reported = new ArrayList<>();

        @Override
        protected void onUnrecognized(@Nonnull ParseOutcome<?> outcome) {
            reported.add("unrecognized");
            super.onUnrecognized(outcome);
        }

        @Override
        protected void onDenied(@Nonnull ParseOutcome<?> outcome) {
            reported.add("denied");
            super.onDenied(outcome);
        }

        @Override
        protected void onInternalError(@Nonnull ParseOutcome<?> outcome) {
            reported.add("internalError");
            super.onInternalError(outcome);
        }
    }

    private ScriptedParser parserFor(ArgRequirementType requirement, boolean fromSender, String def) {
        ArgData argData = new ArgData()
                .setName(requirement == ArgRequirementType.REQUIRED ? "<value>" : "[value]")
                .setContext("")
                .setDef(def)
                .setFromSender(fromSender);
        return new ScriptedParser(ArgInfo.positional(String.class, argData, 0, requirement));
    }

    private ParseCall callOf(ScriptedParser parser, String token) {
        return new ParseCall(sender,
                new Argumento(token),
                parser.getArgInfo(),
                null,
                ResolvedArguments.none(),
                false);
    }

    // ------------------------------------------------------------------
    // A parser about no token at all, answering through the same entry point
    // ------------------------------------------------------------------

    private static class ScriptedContextualParser extends ArgParserContextual<String> {

        final List<String> calls = new ArrayList<>();
        Function<ContextualParseCall, ParseResult<String>> onParse = call -> ParseResult.of("RESOLVED");

        ScriptedContextualParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<String> parse(@Nonnull ContextualParseCall call) {
            calls.add("parse");
            return onParse.apply(call);
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /**
     * The declaration deliberately says {@code fromSender} and carries a {@code def()}: any routing that
     * read the token state instead of the source would pick one of those rows, and a contextual parser
     * has no method to answer them with.
     */
    private ScriptedContextualParser contextualParser() {
        ArgData argData = new ArgData()
                .setName("")
                .setContext("")
                .setDef("fallback")
                .setFromSender(true);
        return new ScriptedContextualParser(ArgInfo.contextual(String.class, argData));
    }

    private ContextualParseCall contextualCallOf(ScriptedContextualParser parser) {
        return new ContextualParseCall(sender, parser.getArgInfo(), null, ResolvedArguments.none(),
                new MultiArgumentos(new String[0]), null, null);
    }

    // ------------------------------------------------------------------
    // The routing table
    // ------------------------------------------------------------------

    @Test
    void aPresentTokenGoesToParse() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, "hello"));

        assertEquals(Arrays.asList("parse(hello)"), parser.calls);
        assertEquals("hello", outcome.getValueOrNull());
        assertFalse(outcome.isFatal());
    }

    @Test
    void anEmptyTokenOnAFromSenderArgumentGoesToFromSender() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, true, "");

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, ""));

        assertEquals(Arrays.asList("fromSender"), parser.calls);
        assertEquals("SENDER", outcome.getValueOrNull());
    }

    @Test
    void anEmptyTokenOnARequiredArgumentIsMissingAndNoParserRuns() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, ""));

        assertTrue(parser.calls.isEmpty(), "there is nothing for a parser to say about a word nobody typed");
        assertEquals(ParseResult.Kind.MISSING, outcome.getResult().getKind());
        assertTrue(outcome.isFatal());
    }

    @Test
    void anEmptyTokenOnAnOptionalArgumentWithDefGoesToParseWithTheDefText() {
        ScriptedParser parser = parserFor(ArgRequirementType.OPTIONAL, false, "fallback");

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, ""));

        assertEquals(Arrays.asList("parse(fallback)"), parser.calls);
        assertEquals("fallback", outcome.getValueOrNull());
    }

    @Test
    void anEmptyTokenOnAnOptionalArgumentWithoutDefGoesToAbsent() {
        ScriptedParser parser = parserFor(ArgRequirementType.OPTIONAL, false, "");

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, ""));

        assertEquals(Arrays.asList("absent"), parser.calls);
        assertEquals("ABSENT", outcome.getValueOrNull());
    }

    @Test
    void aDefTheParserRefusesIsAnInternalErrorThatNamesTheDefault() {
        ScriptedParser parser = parserFor(ArgRequirementType.OPTIONAL, false, "0");
        parser.onParse = call -> ParseResult.denied(new TestLocaleMessage("0 is out of range"));

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, ""));

        assertEquals(ParseResult.Kind.INTERNAL_ERROR, outcome.getResult().getKind(),
                "a default the command itself declared is never the sender's fault, however it was refused");

        String explained = outcome.getResult().getCause().getMessage();
        assertTrue(explained.contains("default '0'"), "the log has to name the default that failed: " + explained);
        assertTrue(explained.contains("DENIED"), "and how the parser refused it: " + explained);
    }

    @Test
    void aContextualParameterIsAskedItsOneQuestionAndNoRowAboveItIsEvenConsidered() {
        ScriptedContextualParser parser = contextualParser();

        ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, contextualCallOf(parser));

        assertEquals(Arrays.asList("parse"), parser.calls,
                "a parameter that takes no token has one method, and the def()/fromSender rows never ran");
        assertEquals("RESOLVED", outcome.getValueOrNull());
        assertFalse(outcome.isFatal());
    }

    /** Nothing routes above a contextual parameter, so there is no such thing as absorbing its miss. */
    @Test
    void aContextualRefusalStaysFatalInsteadOfBeingSoftenedIntoEmpty() {
        ScriptedContextualParser parser = contextualParser();
        parser.onParse = call -> ParseResult.unrecognized(new TestLocaleMessage("the vault is closed"));
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, contextualCallOf(parser));

        assertEquals(ParseResult.Kind.UNRECOGNIZED, outcome.getResult().getKind());
        assertTrue(outcome.isFatal());
        assertEquals(Arrays.asList("unrecognized"), engine.reported);
        sender.assertAnyMessageContains("the vault is closed");
    }

    // ------------------------------------------------------------------
    // What each kind costs
    // ------------------------------------------------------------------

    @Test
    void unrecognizedOnARequiredArgumentIsFatalAndReported() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        parser.onParse = call -> ParseResult.unrecognized(new TestLocaleMessage("no idea what that is"));
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.UNRECOGNIZED, outcome.getResult().getKind());
        assertTrue(outcome.isFatal());
        assertEquals(Arrays.asList("unrecognized"), engine.reported);
        sender.assertAnyMessageContains("no idea what that is");
    }

    @Test
    void unrecognizedOnAnOptionalArgumentBecomesEmptyAndIsNotReported() {
        ScriptedParser parser = parserFor(ArgRequirementType.OPTIONAL, false, "");
        parser.onParse = call -> ParseResult.unrecognized(new TestLocaleMessage("no idea what that is"));
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.EMPTY, outcome.getResult().getKind());
        assertFalse(outcome.isFatal());
        assertNull(outcome.getValueOrNull());
        assertTrue(engine.reported.isEmpty());
        sender.assertNoMessageSent();
    }

    @Test
    void deniedIsFatalOnAnOptionalArgumentToo() {
        ScriptedParser parser = parserFor(ArgRequirementType.OPTIONAL, false, "");
        parser.onParse = call -> ParseResult.denied(new TestLocaleMessage("that one is not allowed"));
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.DENIED, outcome.getResult().getKind());
        assertTrue(outcome.isFatal());
        assertEquals(Arrays.asList("denied"), engine.reported);
        sender.assertAnyMessageContains("that one is not allowed");
    }

    // ------------------------------------------------------------------
    // The safety net
    // ------------------------------------------------------------------

    @Test
    void aRuntimeExceptionFromTheParserBecomesInternalErrorCarryingTheOriginalCause() {
        IllegalStateException boom = new IllegalStateException("the backend is down");
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        parser.onParse = call -> {
            throw boom;
        };
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.INTERNAL_ERROR, outcome.getResult().getKind());
        assertSame(boom, outcome.getResult().getCause());
        assertEquals(Arrays.asList("internalError"), engine.reported);
        assertFalse(sender.getMessages().isEmpty(), "the sender is told something, never the exception");
    }

    @Test
    void anArgParseExceptionThrownFromDeepInsideIsAdoptedAsItsOwnResult() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        parser.onParse = call -> {
            throw new ArgParseException(ParseResult.denied(new TestLocaleMessage("refused three frames down")));
        };
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.DENIED, outcome.getResult().getKind());
        assertEquals(Arrays.asList("denied"), engine.reported);
        sender.assertAnyMessageContains("refused three frames down");
    }

    @Test
    void aThrownUnrecognizedOnAnOptionalArgumentIsNormalizedLikeAReturnedOne() {
        ScriptedParser parser = parserFor(ArgRequirementType.OPTIONAL, false, "");
        parser.onParse = call -> {
            throw new ArgParseException(ParseResult.unrecognized(new TestLocaleMessage("no idea")));
        };
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.EMPTY, outcome.getResult().getKind());
        assertTrue(engine.reported.isEmpty(), "the shortcut buys no different policy");
        sender.assertNoMessageSent();
    }

    @Test
    void aParserThatReturnsNullBecomesInternalErrorNamingTheParser() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        parser.onParse = call -> null;
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.INTERNAL_ERROR, outcome.getResult().getKind());
        assertTrue(outcome.getResult().getCause() instanceof NullPointerException);
        assertTrue(outcome.getResult().getCause().getMessage().contains(ScriptedParser.class.getName()),
                "the message has to name who has to be fixed, but was: " + outcome.getResult().getCause().getMessage());
    }

    /**
     * The reporting is the last thing the engine does and the only part of it that runs outside the
     * safety net. A reason nobody can deliver is still a reason that was never going to change the
     * outcome, so it costs a log line - never the invocation three frames above.
     */
    @Test
    void aReasonThatCannotBeDeliveredCostsALogLineAndNotTheParse() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        parser.onParse = call -> ParseResult.denied((ILocaleMessageBase) null);

        List<String> logged = Logs.capture(() -> {
            ParseOutcome<String> outcome = ParseEngine.DEFAULT.run(parser, callOf(parser, "hello"));

            assertEquals(ParseResult.Kind.DENIED, outcome.getResult().getKind(), "the parse itself stands");
            assertTrue(outcome.isFatal());
        });

        assertTrue(logged.stream().anyMatch(line -> line.contains("failed while reporting")),
                "the broken delivery is not swallowed in silence: " + logged);
    }

    /**
     * The line names who was typing, and a name is not something this code gets to assume the shape
     * of. Pasted into the format string, a {@code {}} inside it consumes the parser's failure as if
     * it were a parameter and the stack trace never reaches the log.
     */
    @Test
    void anInternalErrorKeepsItsStackWhenTheSenderNameCarriesAPlaceholder() {
        sender = new TestCommandSender("St{}eve");
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        parser.onParse = call -> {
            throw new IllegalStateException("the backend is down");
        };

        List<String> logged = Logs.capture(() -> ParseEngine.DEFAULT.run(parser, callOf(parser, "hello")));

        //a stack frame, not the exception's toString: that reaches the line either way
        assertTrue(logged.stream().anyMatch(line -> line.contains("\tat " + ParseEngineTest.class.getName())),
                "the '{}' in the sender name ate the failure; the line carries no stack trace: " + logged);
    }

    @Test
    void missingIsNeverReported() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        RecordingEngine engine = new RecordingEngine();

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, ""));

        assertEquals(ParseResult.Kind.MISSING, outcome.getResult().getKind());
        assertTrue(engine.reported.isEmpty(), "the answer to a word nobody typed is the help line, not a parser's text");
        sender.assertNoMessageSent();
    }

    // ------------------------------------------------------------------
    // The hooks
    // ------------------------------------------------------------------

    @Test
    void beforeParseAndAfterParseRunInOrderAroundTheParser() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        List<String> order = new ArrayList<>();
        parser.onParse = call -> {
            order.add("parse");
            return ParseResult.of("hello");
        };

        ParseEngine engine = new ParseEngine() {
            @Override
            protected void beforeParse(@Nonnull IParseCall call) {
                order.add("before");
            }

            @Override
            protected <T> ParseOutcome<T> afterParse(@Nonnull ParseOutcome<T> outcome) {
                order.add("after");
                return outcome;
            }
        };

        engine.run(parser, callOf(parser, "hello"));

        assertEquals(Arrays.asList("before", "parse", "after"), order);
    }

    @Test
    void afterParseCanReplaceTheOutcome() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");

        ParseEngine engine = new ParseEngine() {
            @Override
            protected <T> ParseOutcome<T> afterParse(@Nonnull ParseOutcome<T> outcome) {
                return outcome.withResult(ParseResult.<T>denied(new TestLocaleMessage("vetoed after the fact")));
            }
        };

        ParseOutcome<String> outcome = engine.run(parser, callOf(parser, "hello"));

        assertEquals(ParseResult.Kind.DENIED, outcome.getResult().getKind());
        assertTrue(outcome.isFatal());
        sender.assertAnyMessageContains("vetoed after the fact");
    }

    @Test
    void anEngineThatOnlyOverridesOnDeniedKeepsTheDefaultForEveryOtherKind() {
        ScriptedParser parser = parserFor(ArgRequirementType.REQUIRED, false, "");
        List<String> swallowed = new ArrayList<>();

        ParseEngine engine = new ParseEngine() {
            @Override
            protected void onDenied(@Nonnull ParseOutcome<?> outcome) {
                swallowed.add("denied");
            }
        };

        parser.onParse = call -> ParseResult.denied(new TestLocaleMessage("refused"));
        engine.run(parser, callOf(parser, "hello"));
        assertEquals(Arrays.asList("denied"), swallowed);
        sender.assertNoMessageSent();

        parser.onParse = call -> ParseResult.unrecognized(new TestLocaleMessage("still unknown"));
        engine.run(parser, callOf(parser, "hello"));
        assertEquals(Arrays.asList("denied"), swallowed, "only the overridden case changed");
        sender.assertAnyMessageContains("still unknown");
    }

    // ------------------------------------------------------------------
    // The source, the parser family and the call have to name the same thing
    // ------------------------------------------------------------------

    /**
     * The mismatch only exists through a RAW parser reference, which is what the framework itself holds
     * (a method's parsers live in a {@code Map<Integer, ArgParser>}): with the type arguments in place
     * the compiler already refuses to pair the two.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ParseOutcome<?> runRaw(ParseEngine engine, AbstractArgParser parser, IParseCall call) {
        return engine.run(parser, call);
    }

    @Test
    void aContextualParserPointedAtANonContextualArgumentIsAnInternalErrorAndNotACast() {
        //A standalone ArgInfo is what a config value carries: it never was a contextual parameter, so
        //routing this parser as one would hand it the ParseCall it cannot read
        ArgData argData = new ArgData().setName("<value>").setContext("").setDef("");
        ScriptedContextualParser parser = new ScriptedContextualParser(ArgInfo.standalone(String.class, argData));

        RecordingEngine engine = new RecordingEngine();
        ParseOutcome<?> outcome = runRaw(engine, parser, new ParseCall(sender, new Argumento("hello"),
                parser.getArgInfo(), null, ResolvedArguments.none(), false));

        assertEquals(ParseResult.Kind.INTERNAL_ERROR, outcome.getResult().getKind());
        assertEquals(Arrays.asList("internalError"), engine.reported);
        assertTrue(parser.calls.isEmpty(), "a mismatched parser is never consulted");
        assertTrue(outcome.getResult().getCause() instanceof ArgMountException,
                "the mismatch is a mount error, not a parse one: " + outcome.getResult().getCause());
        assertTrue(outcome.getResult().getCause().getMessage().contains("STANDALONE"),
                "the message names the source that disagrees: " + outcome.getResult().getCause().getMessage());
    }

    @Test
    void aTokenParserPointedAtAContextualArgumentIsAnInternalErrorToo() {
        ArgData argData = new ArgData().setName("").setContext("").setDef("");
        ScriptedParser parser = new ScriptedParser(ArgInfo.contextual(String.class, argData));

        RecordingEngine engine = new RecordingEngine();
        ParseOutcome<?> outcome = runRaw(engine, parser, new ContextualParseCall(sender, parser.getArgInfo(), null,
                ResolvedArguments.none(), new MultiArgumentos(new String[0]), null, null));

        assertEquals(ParseResult.Kind.INTERNAL_ERROR, outcome.getResult().getKind());
        assertTrue(parser.calls.isEmpty(), "a mismatched parser is never consulted");
    }
}
