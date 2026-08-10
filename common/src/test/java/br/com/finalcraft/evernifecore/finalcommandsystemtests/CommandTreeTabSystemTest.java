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
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tab-complete over a real tree: which position answers what, what the traversal costs, and what it
 * refuses to guess.
 */
class CommandTreeTabSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCMDPluginCommand freshCommand() {
        harness = new FinalCmdTestHarness("TreeTab", tempDir);
        RecordingParser.reset();
        return harness.register(new Tab_Cmd());
    }

    // ------------------------------------------------------------------
    // The tree under test
    // ------------------------------------------------------------------

    /** Answers tab, and counts every OTHER method it is asked for - tab must never ask for one. */
    public static class RecordingParser extends ArgParser<String> {
        static int parseCalls;
        static int absentCalls;
        static int fromSenderCalls;

        static void reset() {
            parseCalls = absentCalls = fromSenderCalls = 0;
        }

        static int nonTabCalls() {
            return parseCalls + absentCalls + fromSenderCalls;
        }

        public RecordingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            parseCalls++;
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public ParseResult<String> absent(@Nonnull ParseCall call) {
            absentCalls++;
            return ParseResult.empty();
        }

        @Override
        public ParseResult<String> fromSender(@Nonnull ParseCall call) {
            fromSenderCalls++;
            return ParseResult.empty();
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            return Arrays.asList("Steve", "Alex").stream()
                    .filter(name -> name.toLowerCase().startsWith(tabContext.getLastWord().toLowerCase()))
                    .collect(Collectors.toList());
        }
    }

    /** Reports back what it was told about the line, so a test can read it off the suggestion. */
    public static class EchoContextParser extends ArgParser<String> {
        public EchoContextParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            return Collections.singletonList("user=" + tabContext.getCaptureToken("user")
                    + "|local=" + tabContext.getLocalIndex()
                    + "|path=" + tabContext.getPath().joined());
        }
    }

    @FinalCMD(aliases = "tabcmd")
    public static class Tab_Cmd {

        @FinalCMD.SubCMD(subcmd = "flat")
        public void flat(FCommandSender sender, @Arg("<value>") Boolean value) {}

        @FinalCMD.SubCMD(subcmd = "tail")
        public void tail(FCommandSender sender,
                         @Arg(value = "<first>", parser = RecordingParser.class) String first,
                         @Arg(value = "<rest...>", parser = EchoContextParser.class) String rest) {}

        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            static String seenTarget;
            static Boolean seenDryRun;

            @FinalCMD.Capture
            public String capture(@Arg(value = "<user>", parser = RecordingParser.class) String user,
                                  @Arg.Flag(value = "--dry-run", aliases = "-n") Boolean dryRun) {
                seenTarget = user;
                seenDryRun = dryRun;
                return user;
            }

            @FinalCMD.SubCMD(subcmd = "info")
            public void info(FCommandSender sender, @Arg.NodeCaptured String target) {}

            @FinalCMD.Node(subcmd = "home")
            public static class HomeNode {
                @FinalCMD.SubCMD(subcmd = "go")
                public void go(FCommandSender sender,
                               @Arg.NodeCaptured String target,
                               @Arg(value = "<home>", parser = EchoContextParser.class) String home) {}
            }
        }

        @FinalCMD.Node(subcmd = "pair")
        public static class PairNode {
            @FinalCMD.Capture
            public String capture(@Arg(value = "<server>", parser = RecordingParser.class) String server,
                                  @Arg(value = "<realm>", parser = EchoContextParser.class) String realm) {
                return server + ":" + realm;
            }

            @FinalCMD.SubCMD(subcmd = "show")
            public void show(FCommandSender sender, @Arg.NodeCaptured String pair) {}
        }

        @FinalCMD.Node(subcmd = "secret")
        public static class SecretNode {
            @FinalCMD.SubCMD(subcmd = "hidden", permission = "tab.hidden")
            public void hidden(FCommandSender sender) {}
        }
    }

    // ------------------------------------------------------------------
    // One position at a time, all the way down
    // ------------------------------------------------------------------

    @Test
    void everyPositionOfTheTreeAnswersWithWhatBelongsThere() {
        FinalCMDPluginCommand command = freshCommand();
        TestCommandSender sender = new TestCommandSender("console").grant("tab.hidden");

        //depth 0, a literal
        assertEquals(Arrays.asList("flat", "pair", "secret", "tail", "user"), harness.tab(command, sender, ""));
        assertEquals(Arrays.asList("user"), harness.tab(command, sender, "us"));

        //depth 1, the token a capture eats
        assertEquals(Arrays.asList("Steve", "Alex"), harness.tab(command, sender, "user", ""));
        assertEquals(Arrays.asList("Steve"), harness.tab(command, sender, "user", "st"));

        //depth 1 again, now past the capture: a literal once more
        assertEquals(Arrays.asList("home", "info"), harness.tab(command, sender, "user", "Steve", ""));

        //depth 2, a literal under a node with no capture of its own
        assertEquals(Arrays.asList("go"), harness.tab(command, sender, "user", "Steve", "home", ""));

        //depth 3, the leaf's own positional 0
        assertEquals(Arrays.asList("user=Steve|local=0|path=user Steve home go"),
                harness.tab(command, sender, "user", "Steve", "home", "go", ""));

        //each token of a two-token capture answers with its OWN parser
        assertEquals(Arrays.asList("Steve", "Alex"), harness.tab(command, sender, "pair", ""));
        assertEquals(Arrays.asList("user=null|local=1|path=pair"), harness.tab(command, sender, "pair", "srv", ""));
        assertEquals(Arrays.asList("show"), harness.tab(command, sender, "pair", "srv", "realm", ""));

        //a leaf's positionals, 0 and then the variadic tail, which is sticky from its own index on
        assertEquals(Arrays.asList("Steve", "Alex"), harness.tab(command, sender, "tail", ""));
        assertEquals(Arrays.asList("user=null|local=1|path=tail"), harness.tab(command, sender, "tail", "a", ""));
        assertEquals(Arrays.asList("user=null|local=2|path=tail"), harness.tab(command, sender, "tail", "a", "b", ""));
        assertEquals(Arrays.asList("user=null|local=3|path=tail"), harness.tab(command, sender, "tail", "a", "b", "c", ""));

        //a leaf whose @Arg has a builtin parser still answers from it
        assertEquals(Arrays.asList("false", "true"), harness.tab(command, sender, "flat", ""));
    }

    @Test
    void tabNeverParsesAnything() {
        FinalCMDPluginCommand command = freshCommand();
        TestCommandSender sender = new TestCommandSender("console").grant("tab.hidden");

        harness.tab(command, sender, "");
        harness.tab(command, sender, "user", "");
        harness.tab(command, sender, "user", "Steve", "");
        harness.tab(command, sender, "user", "Steve", "home", "go", "");
        harness.tab(command, sender, "pair", "srv", "");
        harness.tab(command, sender, "tail", "a", "b", "");
        harness.tab(command, sender, "flat", "");

        assertEquals(0, RecordingParser.nonTabCalls(),
                "tab ran parse=" + RecordingParser.parseCalls
                        + " absent=" + RecordingParser.absentCalls + " fromSender=" + RecordingParser.fromSenderCalls);
        assertTrue(sender.getMessages().isEmpty(), "tab must never message the sender");
    }

    @Test
    void aCapturedTokenComesBackRawEvenWhenNothingCouldResolveIt() {
        FinalCMDPluginCommand command = freshCommand();
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(Arrays.asList("user=NoSuchPlayer#1|local=0|path=user NoSuchPlayer#1 home go"),
                harness.tab(command, sender, "user", "NoSuchPlayer#1", "home", "go", ""));
        assertEquals(0, RecordingParser.nonTabCalls(), "an unresolvable token is still never parsed");
    }

    // ------------------------------------------------------------------
    // What tab hides, and what it refuses to guess
    // ------------------------------------------------------------------

    @Test
    void aBranchIsListedOnlyWhileSomethingUnderItIsReachable() {
        FinalCMDPluginCommand command = freshCommand();

        TestCommandSender denied = new TestCommandSender("console");
        assertTrue(harness.tab(command, denied, "sec").isEmpty(), "a branch whose every leaf is denied is not a place to go");

        TestCommandSender allowed = new TestCommandSender("console").grant("tab.hidden");
        assertEquals(Arrays.asList("secret"), harness.tab(command, allowed, "sec"));
        assertEquals(Arrays.asList("hidden"), harness.tab(command, allowed, "secret", ""));
    }

    @Test
    void aLineTheTreeCannotPlaceAnswersNothingAtAll() {
        FinalCMDPluginCommand command = freshCommand();
        TestCommandSender sender = new TestCommandSender("console").grant("tab.hidden");

        assertTrue(harness.tab(command, sender, "nosuchthing", "").isEmpty(), "an unmatched literal leads nowhere");
        assertTrue(harness.tab(command, sender, "user", "Steve", "nosuchthing", "").isEmpty());
        assertTrue(harness.tab(command, sender, "user", "Steve", "info", "").isEmpty(), "a leaf with no @Arg has no position to fill");
    }

    @Test
    void aCaptureTakesAHelpWordAsItsTokenLikeAnyOther() {
        FinalCMDPluginCommand command = freshCommand();
        TestCommandSender sender = new TestCommandSender("console");

        //the capture standing where the word was typed claims it, so the line goes on below the node -
        //a player named like a help word has to be addressable
        assertEquals(Arrays.asList("home", "info"), harness.tab(command, sender, "user", "help", ""));
    }

    // ------------------------------------------------------------------
    // A flag declared on a node is a flag of its whole subtree
    // ------------------------------------------------------------------

    @Test
    void aNodesFlagIsOfferedAtAGrandchildLeafAndReachesItsCapture() {
        FinalCMDPluginCommand command = freshCommand();
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(Arrays.asList("--dry-run"), harness.tab(command, sender, "user", "Steve", "home", "go", "somewhere", "--"));
        assertTrue(harness.tab(command, sender, "user", "Steve", "home", "go", "somewhere", "--dry-run", "--").isEmpty(),
                "a flag already on the line is not offered twice");

        Tab_Cmd.UserNode.seenDryRun = null;
        harness.dispatch(command, sender, "user Steve home go somewhere --dry-run");
        assertEquals(Boolean.TRUE, Tab_Cmd.UserNode.seenDryRun, "the node's own capture is what receives it");
        assertEquals("Steve", Tab_Cmd.UserNode.seenTarget);

        Tab_Cmd.UserNode.seenDryRun = null;
        harness.dispatch(command, sender, "user Steve home go somewhere -n");
        assertEquals(Boolean.TRUE, Tab_Cmd.UserNode.seenDryRun, "the alias is the same flag");

        Tab_Cmd.UserNode.seenDryRun = null;
        harness.dispatch(command, sender, "user Steve home go somewhere");
        assertEquals(null, Tab_Cmd.UserNode.seenDryRun, "an absent flag is absent, not false");
    }
}
