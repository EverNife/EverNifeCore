package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolutionPhase;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a parameter read off the invocation sits relative to the ones read off the line, and what each
 * side can see from there. The whole point of the default order is that a token's parser can read what
 * the invocation already produced; the whole point of the override is that the reverse is still
 * reachable, for the parser whose answer depends on a token.
 */
class ContextualResolutionPhaseTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("Phase", tempDir);
        Recorder.reset();
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    /** A value no token could ever spell - it exists only to be read off the invocation. */
    public static class Ticket {
        final String origin;

        Ticket(String origin) {
            this.origin = origin;
        }
    }

    /** What an ancestor node captured, as a type nothing else in this test produces. */
    public static class UserRef {
        final String name;

        UserRef(String name) {
            this.name = name;
        }
    }

    /** What ran, in the order it ran, and what each step could already see when it did. */
    static class Recorder {
        static final List<String> steps = new ArrayList<>();

        static UserRef captureSeenByEarly;
        static String tokenSeenByEarly;

        static Ticket earlySeenByTokenByType;
        static Ticket earlySeenByTokenByName;
        static UserRef captureSeenByToken;

        static String tokenSeenByLate;

        static void reset() {
            steps.clear();
            captureSeenByEarly = null;
            tokenSeenByEarly = null;
            earlySeenByTokenByType = null;
            earlySeenByTokenByName = null;
            captureSeenByToken = null;
            tokenSeenByLate = null;
        }
    }

    public static class EarlyParser extends ArgParserContextual<Ticket> {
        public EarlyParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Ticket> parse(@Nonnull ContextualParseCall call) {
            Recorder.steps.add("contextual-early");
            Recorder.captureSeenByEarly = call.previouslyParsed(UserRef.class);
            Recorder.tokenSeenByEarly = call.previouslyParsed("<token>", String.class);
            return ParseResult.of(new Ticket("early"));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    public static class LateParser extends ArgParserContextual<Ticket> {
        public LateParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Ticket> parse(@Nonnull ContextualParseCall call) {
            Recorder.steps.add("contextual-late");
            Recorder.tokenSeenByLate = call.previouslyParsed("<token>", String.class);
            return ParseResult.of(new Ticket("late"));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /** Serves both the flag and the positional, and says which one it was answering. */
    public static class RecordingParser extends ArgParser<String> {
        public RecordingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<String> parse(@Nonnull ParseCall call) {
            String declaredName = call.getArgInfo().getArgData().getName();
            Recorder.steps.add(declaredName);

            if (!call.isFlagValue()){
                Recorder.earlySeenByTokenByType = call.previouslyParsed(Ticket.class);
                Recorder.earlySeenByTokenByName = call.previouslyParsed("early", Ticket.class);
                Recorder.captureSeenByToken = call.previouslyParsed(UserRef.class);
            }

            return ParseResult.of(call.getArgumento().toString());
        }
    }

    @FinalCMD(aliases = "ordercmd")
    public static class Order_Cmd {
        static boolean invoked = false;

        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            @FinalCMD.Capture
            public UserRef capture(@Arg("<user>") String user) {
                return new UserRef(user);
            }

            @FinalCMD.SubCMD(subcmd = "go")
            public void go(FCommandSender sender,
                           @Arg.Contextual(value = "early", parser = EarlyParser.class) Ticket early,
                           @Arg.Flag(value = "--note", parser = RecordingParser.class) String note,
                           @Arg.NodeCaptured UserRef ref,
                           @Arg(value = "<token>", parser = RecordingParser.class) String token,
                           @Arg.Contextual(value = "late", parser = LateParser.class,
                                   phase = ResolutionPhase.AFTER_ARGUMENTS) Ticket late) {
                invoked = true;
            }
        }
    }

    private void dispatchOrderCmd() {
        Order_Cmd.invoked = false;
        FinalCMDPluginCommand command = harness.register(new Order_Cmd());
        harness.dispatch(command, new TestCommandSender("console"), "user Steve go --note hello typed");
        assertTrue(Order_Cmd.invoked, "the whole invocation ran: " + Recorder.steps);
    }

    @Test
    void theInvocationResolvesCapturesContextualsFlagsPositionalsThenLateContextuals() {
        dispatchOrderCmd();

        assertEquals(Arrays.asList("contextual-early", "--note", "<token>", "contextual-late"), Recorder.steps);
        //The capture step consults no parser, so it leaves no step of its own - what pins it first is
        //that everything after it could already read what it produced
        assertNotNull(Recorder.captureSeenByToken, "the capture was in the bag before the token was parsed");
        assertEquals("Steve", Recorder.captureSeenByToken.name);
    }

    @Test
    void aTokensParserReadsWhatAnEarlyContextualResolved() {
        dispatchOrderCmd();

        assertNotNull(Recorder.earlySeenByTokenByType, "by class");
        assertEquals("early", Recorder.earlySeenByTokenByType.origin);
        assertNotNull(Recorder.earlySeenByTokenByName, "by declared name");
        assertEquals("early", Recorder.earlySeenByTokenByName.origin);
    }

    /**
     * The price of running before the line is read, and it is a contract rather than an accident: a
     * parameter that resolves first sees nothing of the line, and a parser that needs a token says so
     * by declaring {@link ResolutionPhase#AFTER_ARGUMENTS}. A capture is not part of that price - it
     * was resolved while the path was being walked, long before any parameter of this method.
     */
    @Test
    void anEarlyContextualSeesTheCaptureButNotYetTheToken() {
        dispatchOrderCmd();

        assertNull(Recorder.tokenSeenByEarly, "the token had not been parsed yet");
        assertNotNull(Recorder.captureSeenByEarly, "the capture was already resolved by the walk");
        assertEquals("Steve", Recorder.captureSeenByEarly.name);
    }

    @Test
    void aLateContextualReadsATokenThatWasAlreadyParsed() {
        dispatchOrderCmd();

        assertEquals("typed", Recorder.tokenSeenByLate);
    }

    // ------------------------------------------------------------------
    // The phase lives on the parser, and the annotation overrides it
    // ------------------------------------------------------------------

    /** A parser that would rather see the tokens, for every parameter that does not say otherwise. */
    public static class LateByDefaultParser extends ArgParserContextual<Ticket> {
        static String sawToken;
        static boolean ran;

        public LateByDefaultParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Ticket> parse(@Nonnull ContextualParseCall call) {
            ran = true;
            sawToken = call.previouslyParsed("<token>", String.class);
            return ParseResult.of(new Ticket("late-by-default"));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }

        @Override
        public ResolutionPhase defaultPhase() {
            return ResolutionPhase.AFTER_ARGUMENTS;
        }
    }

    /** Inherits the early default - the one most contextual parameters want. */
    public static class EarlyByDefaultParser extends ArgParserContextual<Ticket> {
        static String sawToken;
        static boolean ran;

        public EarlyByDefaultParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Ticket> parse(@Nonnull ContextualParseCall call) {
            ran = true;
            sawToken = call.previouslyParsed("<token>", String.class);
            return ParseResult.of(new Ticket("early-by-default"));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    @FinalCMD(aliases = "phasecmd")
    public static class Phase_Cmd {

        /** Nothing declared: the parser's own answer is what places the parameter. */
        @FinalCMD.SubCMD(subcmd = "parserchooses")
        public void parserChooses(FCommandSender sender,
                                  @Arg.Contextual(value = "ticket", parser = LateByDefaultParser.class) Ticket ticket,
                                  @Arg("<token>") String token) {
        }

        /** The parameter drags a late-by-default parser in front of the tokens. */
        @FinalCMD.SubCMD(subcmd = "draggedearly")
        public void draggedEarly(FCommandSender sender,
                                 @Arg.Contextual(value = "ticket", parser = LateByDefaultParser.class,
                                         phase = ResolutionPhase.BEFORE_ARGUMENTS) Ticket ticket,
                                 @Arg("<token>") String token) {
        }

        /** The parameter pushes an early-by-default parser behind the tokens. */
        @FinalCMD.SubCMD(subcmd = "pushedlate")
        public void pushedLate(FCommandSender sender,
                               @Arg.Contextual(value = "ticket", parser = EarlyByDefaultParser.class,
                                       phase = ResolutionPhase.AFTER_ARGUMENTS) Ticket ticket,
                               @Arg("<token>") String token) {
        }
    }

    private void dispatchPhaseCmd(String subcmd) {
        LateByDefaultParser.ran = false;
        LateByDefaultParser.sawToken = null;
        EarlyByDefaultParser.ran = false;
        EarlyByDefaultParser.sawToken = null;

        FinalCMDPluginCommand command = harness.register(new Phase_Cmd());
        harness.dispatch(command, new TestCommandSender("console"), subcmd + " typed");
    }

    @Test
    void aParserThatAsksToRunLateRunsLateWhereTheParameterSaysNothing() {
        dispatchPhaseCmd("parserchooses");

        assertTrue(LateByDefaultParser.ran);
        assertEquals("typed", LateByDefaultParser.sawToken);
    }

    @Test
    void thePhaseOnTheParameterOverridesAParserThatWantedToRunLate() {
        dispatchPhaseCmd("draggedearly");

        assertTrue(LateByDefaultParser.ran);
        assertNull(LateByDefaultParser.sawToken, "the parameter moved it in front of the tokens");
    }

    @Test
    void thePhaseOnTheParameterOverridesAParserThatWantedToRunEarly() {
        dispatchPhaseCmd("pushedlate");

        assertTrue(EarlyByDefaultParser.ran);
        assertEquals("typed", EarlyByDefaultParser.sawToken, "the parameter moved it behind the tokens");
    }

    // ------------------------------------------------------------------
    // PARSER_DEFAULT is the question, not an answer to it
    // ------------------------------------------------------------------

    public static class UndecidedParser extends ArgParserContextual<Ticket> {
        public UndecidedParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Ticket> parse(@Nonnull ContextualParseCall call) {
            return ParseResult.of(new Ticket("undecided"));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }

        @Override
        public ResolutionPhase defaultPhase() {
            return ResolutionPhase.PARSER_DEFAULT;
        }
    }

    @FinalCMD(aliases = "undecidedcmd")
    public static class Undecided_Cmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg.Contextual(value = "ticket", parser = UndecidedParser.class) Ticket ticket) {
        }
    }

    /**
     * A parameter saying {@code PARSER_DEFAULT} is asking the parser; a parser saying it back has
     * answered nothing, and a phase picked for it would be a guess nobody could see.
     */
    @Test
    void aParserThatHandsTheChoiceBackIsRefusedAtRegistration() {
        String message = harness.registerExpectingError(new Undecided_Cmd()).getMessage();

        assertTrue(message.contains("UndecidedParser"), message);
        assertTrue(message.contains("defaultPhase()"), message);
        assertTrue(message.contains(ResolutionPhase.BEFORE_ARGUMENTS.name()), "it names what to return instead: " + message);
    }

    // ------------------------------------------------------------------
    // The variadic tail is a positional like any other: it goes into the bag
    // ------------------------------------------------------------------

    public static class TailReadingParser extends ArgParserContextual<Ticket> {
        static String tailByName;
        static String lastStringByType;

        public TailReadingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Ticket> parse(@Nonnull ContextualParseCall call) {
            tailByName = call.previouslyParsed("<reason...>", String.class);
            lastStringByType = call.previouslyParsed(String.class);
            return ParseResult.of(new Ticket("tail-reader"));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }

        @Override
        public ResolutionPhase defaultPhase() {
            return ResolutionPhase.AFTER_ARGUMENTS;
        }
    }

    @FinalCMD(aliases = "tailbagcmd")
    public static class TailBag_Cmd {
        @FinalCMD.SubCMD(subcmd = "ban")
        public void ban(FCommandSender sender,
                        @Arg("<player>") String player,
                        @Arg("<reason...>") String reason,
                        @Arg.Contextual(value = "reader", parser = TailReadingParser.class) Ticket reader) {
        }
    }

    /**
     * A late contextual exists to read what the line said, and the tail is the one thing on the line
     * most worth reading. Asking by type used to answer with the previous String positional - a wrong
     * value that looks perfectly plausible.
     */
    @Test
    void aLateContextualReadsTheVariadicTailByNameAndByType() {
        TailReadingParser.tailByName = null;
        TailReadingParser.lastStringByType = null;
        FinalCMDPluginCommand command = harness.register(new TailBag_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "ban Notch grief repetido");

        assertEquals("grief repetido", TailReadingParser.tailByName, "by declared name");
        assertEquals("grief repetido", TailReadingParser.lastStringByType, "by type: the tail was the most recent String");
    }
}
