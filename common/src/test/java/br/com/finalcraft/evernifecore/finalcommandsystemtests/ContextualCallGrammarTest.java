package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A parser resolving a parameter from the invocation asks the same questions, under the same names,
 * that a parser converting a token asks - and gets the answers a second grammar never gave it at all.
 */
class ContextualCallGrammarTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("Grammar", tempDir);
        Report.reset();
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    /** Whatever the parser managed to see, so the test reads it instead of guessing from a message. */
    public static class Report {
        static String capturedUser;
        static String mostRecentString;
        static String namedFrom;
        static String namedTo;
        static String described;
        static boolean reached;

        static void reset() {
            capturedUser = null;
            mostRecentString = null;
            namedFrom = null;
            namedTo = null;
            described = null;
            reached = false;
        }
    }

    public static class ReportingParser extends ArgParserContextual<Report> {
        public ReportingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Report> parse(@Nonnull ContextualParseCall call) {
            Report.capturedUser = call.captured("user", String.class);
            Report.mostRecentString = call.previouslyParsed(String.class);
            Report.namedFrom = call.previouslyParsed("<from>", String.class);
            Report.namedTo = call.previouslyParsed("<to>", String.class);
            Report.described = call.describeArgument();
            Report.reached = true;
            return ParseResult.of(new Report());
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    @FinalCMD(aliases = "grammarcmd")
    public static class Grammar_Cmd {
        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                return user;
            }

            //AFTER_ARGUMENTS because the questions pinned here include what the tokens resolved to, and
            //a contextual parameter only sees those when it is declared to run after them
            @FinalCMD.SubCMD(subcmd = "move")
            public void move(FCommandSender sender,
                             @Arg("<from>") String from,
                             @Arg("<to>") String to,
                             @Arg.Contextual(value = "report", parser = ReportingParser.class,
                                     phase = ResolutionPhase.AFTER_ARGUMENTS) Report report) {
            }
        }
    }

    /**
     * What an ancestor captured used to be unreachable from a contextual parser: the only way in was
     * a {@code @Arg.NodeCaptured} parameter on the method itself, which is the method's business and
     * not the parser's.
     */
    @Test
    void aContextualParserReadsWhatAnAncestorNodeCaptured() {
        FinalCMDPluginCommand command = harness.register(new Grammar_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "user Steve move alpha omega");

        assertTrue(Report.reached, "the parser ran");
        assertEquals("Steve", Report.capturedUser);
    }

    @Test
    void aTypeLookupAnswersTheMostRecentAndANameLookupAnswersTheExactOne() {
        FinalCMDPluginCommand command = harness.register(new Grammar_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "user Steve move alpha omega");

        assertEquals("omega", Report.mostRecentString, "two Strings were resolved; the type lookup takes the latest");
        assertEquals("alpha", Report.namedFrom);
        assertEquals("omega", Report.namedTo);
    }

    @Test
    void anAnnotatedContextualParameterIsNamedByWhatItDeclared() {
        FinalCMDPluginCommand command = harness.register(new Grammar_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "user Steve move alpha omega");

        assertEquals("report", Report.described);
    }

    // ------------------------------------------------------------------
    // The usual case: a parameter with no annotation at all
    // ------------------------------------------------------------------

    public static class Widget {
    }

    public static class DescribingParser extends ArgParserContextual<Widget> {
        static String described;

        public DescribingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            described = call.describeArgument();
            return ParseResult.of(new Widget());
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    @FinalCMD(aliases = "unnamedcmd")
    public static class Unnamed_Cmd {
        @FinalCMD.SubCMD(subcmd = "run")
        public void run(FCommandSender sender, Widget widget) {
        }
    }

    @Test
    void aParameterWithNoAnnotationIsNamedByItsType() {
        //Registered on THIS harness's plugin only, so the lookup that resolves an unannotated
        //parameter is exercised without a global registration outliving the test
        ArgParserManager.addPluginContextualParser(harness.ecPluginData, Widget.class, DescribingParser.class);
        DescribingParser.described = null;

        FinalCMDPluginCommand command = harness.register(new Unnamed_Cmd());
        harness.dispatch(command, new TestCommandSender("console"), "run");

        assertEquals("Widget", DescribingParser.described);
    }

    // ------------------------------------------------------------------
    // A name is how a value is addressed, so a method cannot declare one twice
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "clashcmd")
    public static class NameClash_Cmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg("<user>") String first,
                         @Arg.Contextual(value = "<user>", parser = ReportingParser.class) Report second) {
        }
    }

    @Test
    void twoParametersOfOneMethodCannotAnswerToTheSameName() {
        String message = harness.registerExpectingError(new NameClash_Cmd()).getMessage();

        assertTrue(message.contains("<user>"), message);
        assertTrue(message.contains("index=1"), "the message names both parameters: " + message);
        assertTrue(message.contains("index=2"), "the message names both parameters: " + message);
    }

    @FinalCMD(aliases = "bothspellingscmd")
    public static class FlagAndPositional_Cmd {
        static Integer seenPositional;
        static Integer seenFlag;

        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg("<amount>") Integer positional,
                         @Arg.Flag("--amount") Integer flag) {
            seenPositional = positional;
            seenFlag = flag;
        }
    }

    /** A flag and a positional are two different words to type, so they never claim each other's name. */
    @Test
    void aFlagSpellingNeverClashesWithAPositionalOfTheSameWord() {
        FinalCMDPluginCommand command = harness.register(new FlagAndPositional_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "leaf 10 --amount 20");

        assertEquals(10, (int) FlagAndPositional_Cmd.seenPositional);
        assertEquals(20, (int) FlagAndPositional_Cmd.seenFlag);
    }
}
