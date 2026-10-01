package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A word that starts with a dash is the sender's text unless a flag is declared to read it: a path that
 * declares no flag hands every token over as typed, and a path that does only takes a single-dash word
 * when it spells one of its flags - a {@code --name} is still a flag, so a misspelled one is refused.
 */
class DashedWordSystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("DashedWord", tempDir);
        return harness;
    }

    @FinalCMD(aliases = "freefilter")
    public static class NoFlagAnywhere_Cmd {
        static List<String> words;
        static String first;

        @FinalCMD.SubCMD(subcmd = "add")
        public void add(FCommandSender sender, @Arg("<first>") String first, @Arg("[rest...]") List<String> rest) {
            NoFlagAnywhere_Cmd.first = first;
            NoFlagAnywhere_Cmd.words = rest;
        }

        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender, @Arg("<word>") String word, @Arg(value = "<mode>", context = "fast|slow") String mode) {
            NoFlagAnywhere_Cmd.first = word + ":" + mode;
        }
    }

    /** A dashed word the walker meets before any method: at the root, and inside a node's capture. */
    public static class PathCmd {
        static String received;

        @FinalCMD(aliases = "freepath")
        public void root(FCommandSender sender, @Arg("[word]") String word) {
            received = "root:" + word;
        }

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            received = "sub";
        }

        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                return user;
            }

            @FinalCMD.SubCMD(subcmd = "info")
            public void info(FCommandSender sender, @Arg.NodeCaptured String user) {
                received = "info:" + user;
            }
        }
    }

    @FinalCMD(aliases = "mixedfilter")
    public static class OneSubcommandWithFlags_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "add")
        public void add(FCommandSender sender, @Arg("<word>") String word) {
            received = word;
        }

        @FinalCMD.SubCMD(subcmd = "list")
        public void list(FCommandSender sender, @Arg.Flag("--all") Boolean all) {
            received = String.valueOf(all);
        }
    }

    /** Free text and flags on the same line: a chat command with a variadic message. */
    @FinalCMD(aliases = "talk")
    public static class Chat_Cmd {
        static String channel;
        static String message;
        static Boolean loud;
        static String prefix;

        @FinalCMD.SubCMD(subcmd = "say")
        public void say(FCommandSender sender,
                        @Arg(value = "<channel>", context = "global|local") String channel,
                        @Arg("<message...>") String message,
                        @Arg.Flag(value = "--loud", aliases = "-l", def = "false") Boolean loud,
                        @Arg.Flag("--prefix") String prefix) {
            Chat_Cmd.channel = channel;
            Chat_Cmd.message = message;
            Chat_Cmd.loud = loud;
            Chat_Cmd.prefix = prefix;
        }

        static void reset() {
            channel = null;
            message = null;
            loud = null;
            prefix = null;
        }
    }

    /** What a method migrated from reading flags by hand still does: ask the line for one it never declared. */
    @FinalCMD(aliases = "handread")
    public static class ReadsAnUndeclaredFlag_Cmd {
        static IllegalStateException refused;

        @FinalCMD.SubCMD(subcmd = "buy")
        public void buy(FCommandSender sender, MultiArgumentos argumentos) {
            try {
                argumentos.getFlag("confirm");
            } catch (IllegalStateException e) {
                refused = e;
            }
        }
    }

    // ------------------------------------------------------------------
    // A path that declares no flag: every token is text
    // ------------------------------------------------------------------

    @Test
    void everyTokenIsPositionalTheBareDoubleDashIncluded() {
        FinalCMDPluginCommand command = newHarness().register(new NoFlagAnywhere_Cmd());
        NoFlagAnywhere_Cmd.words = null;
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "add -zombie -- --foo");

        assertEquals("-zombie", NoFlagAnywhere_Cmd.first);
        assertEquals(List.of("--", "--foo"), NoFlagAnywhere_Cmd.words);
        sender.assertNoMessageSent();
    }

    @Test
    void theTabCountsADashedWordAsAPositional() {
        FinalCMDPluginCommand command = newHarness().register(new NoFlagAnywhere_Cmd());

        assertTrue(harness.tab(command, new TestCommandSender("console"), "add", "-zombie", "--").isEmpty(),
                "no flag names are offered, and nothing is refused");
        assertEquals(List.of("fast", "slow"), harness.tab(command, new TestCommandSender("console"), "set", "-zombie", ""),
                "-zombie filled <word>, so the next word completes <mode>");
    }

    @Test
    void aDashedWordOnThePathIsAWordToo() {
        FinalCMDPluginCommand command = newHarness().register(new PathCmd());
        TestCommandSender sender = new TestCommandSender("console");

        PathCmd.received = null;
        harness.dispatch(command, sender, "-x");
        assertEquals("root:-x", PathCmd.received, "the root's own method takes it, no 'flag too early'");

        PathCmd.received = null;
        harness.dispatch(command, sender, "--x");
        assertEquals("root:--x", PathCmd.received, "with no flag anywhere, two dashes are a word as well");

        PathCmd.received = null;
        harness.dispatch(command, sender, "user -steve info");
        assertEquals("info:-steve", PathCmd.received, "a capture eats a dashed word like any other");
    }

    @Test
    void onlyTheSubcommandThatDeclaresAFlagReadsOne() {
        FinalCMDPluginCommand command = newHarness().register(new OneSubcommandWithFlags_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        OneSubcommandWithFlags_Cmd.received = null;
        harness.dispatch(command, sender, "add -foo");
        assertEquals("-foo", OneSubcommandWithFlags_Cmd.received);

        OneSubcommandWithFlags_Cmd.received = null;
        harness.dispatch(command, sender, "add --all");
        assertEquals("--all", OneSubcommandWithFlags_Cmd.received, "a sibling's flag means nothing on this path");

        OneSubcommandWithFlags_Cmd.received = null;
        harness.dispatch(command, sender, "list --all");
        assertEquals("true", OneSubcommandWithFlags_Cmd.received, "the sibling keeps its flags");
    }

    @Test
    void beforeThePathOnlyALongMarkerIsAFlagWrittenTooEarly() {
        FinalCMDPluginCommand command = newHarness().register(new OneSubcommandWithFlags_Cmd());

        TestCommandSender singleDash = new TestCommandSender("console");
        harness.dispatch(command, singleDash, "-foo");
        singleDash.assertAnyMessageContains("-foo");
        assertFalse(singleDash.anyMessageContains("comes after the subcommand"),
                "-foo spells no flag below, so it is a subcommand nobody declared: " + singleDash.getMessages());

        TestCommandSender doubleDash = new TestCommandSender("console");
        harness.dispatch(command, doubleDash, "--all list");
        doubleDash.assertAnyMessageContains("comes after the subcommand that declares it");
    }

    // ------------------------------------------------------------------
    // A path that declares flags: a single dash is a flag only when it spells one
    // ------------------------------------------------------------------

    @Test
    void aSingleDashWordNothingDeclaresOpensTheMessage() {
        FinalCMDPluginCommand command = newHarness().register(new Chat_Cmd());
        Chat_Cmd.reset();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "say global -lol hi");

        assertEquals("global", Chat_Cmd.channel);
        assertEquals("-lol hi", Chat_Cmd.message);
        assertEquals(Boolean.FALSE, Chat_Cmd.loud);
        sender.assertNoMessageSent();
    }

    @Test
    void aDeclaredSpellingWithOneDashIsStillTheFlag() {
        FinalCMDPluginCommand command = newHarness().register(new Chat_Cmd());

        Chat_Cmd.reset();
        harness.dispatch(command, new TestCommandSender("console"), "say global -l hi");
        assertEquals(Boolean.TRUE, Chat_Cmd.loud, "the declared alias");
        assertEquals("hi", Chat_Cmd.message);

        Chat_Cmd.reset();
        harness.dispatch(command, new TestCommandSender("console"), "say global -loud hi");
        assertEquals(Boolean.TRUE, Chat_Cmd.loud, "the long name typed with one dash");
        assertEquals("hi", Chat_Cmd.message);
    }

    @Test
    void aMisspelledLongFlagIsStillRefused() {
        FinalCMDPluginCommand command = newHarness().register(new Chat_Cmd());
        Chat_Cmd.reset();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "say global --lod hi");

        assertEquals(null, Chat_Cmd.message, "nothing runs on a flag nobody declared");
        sender.assertAnyMessageContains("--lod");
        sender.assertAnyMessageContains("--loud");
    }

    @Test
    void aValueFlagTakesASingleDashWordAsItsValue() {
        FinalCMDPluginCommand command = newHarness().register(new Chat_Cmd());
        Chat_Cmd.reset();

        harness.dispatch(command, new TestCommandSender("console"), "say global --prefix -x hi");

        assertEquals("-x", Chat_Cmd.prefix);
        assertEquals("hi", Chat_Cmd.message);
    }

    @Test
    void theTabCountsASingleDashWordNothingDeclaresAsAPositional() {
        FinalCMDPluginCommand command = newHarness().register(new Chat_Cmd());

        assertFalse(harness.tab(command, new TestCommandSender("console"), "say", "-lol", "").contains("global"),
                "-lol filled <channel>, so the word being typed is the message, not <channel> again");
        assertEquals(List.of("global", "local"), harness.tab(command, new TestCommandSender("console"), "say", "-l", ""),
                "-l is the declared alias, so <channel> is still the word being typed");
    }

    // ------------------------------------------------------------------
    // Reading a flag nobody declared is a bug, said at the first read
    // ------------------------------------------------------------------

    @Test
    void readingAnUndeclaredFlagOffTheDispatchedLineIsRefused() {
        FinalCMDPluginCommand command = newHarness().register(new ReadsAnUndeclaredFlag_Cmd());
        ReadsAnUndeclaredFlag_Cmd.refused = null;

        harness.dispatch(command, new TestCommandSender("console"), "buy -confirm");

        assertTrue(ReadsAnUndeclaredFlag_Cmd.refused != null, "the lookup could only come back empty, so it throws");
        assertTrue(ReadsAnUndeclaredFlag_Cmd.refused.getMessage().contains("@Arg.Flag(\"--confirm\")"),
                ReadsAnUndeclaredFlag_Cmd.refused.getMessage());
    }

    @Test
    void aScannedLineAnswersTheFlagsItWasScannedForAndNothingElse() {
        MultiArgumentos line = new MultiArgumentos(new String[]{"x", "-p", "2"});
        line.extractDeclaredFlags(Collections.singletonMap("p", new MultiArgumentos.FlagBinding("page", 1, false)), -1);

        assertEquals("2", line.getFlag("--page").getFlagValue(), "the alias -p was stored under the canonical name");
        assertThrows(IllegalStateException.class, () -> line.getFlag("--other"));

        MultiArgumentos sniffed = new MultiArgumentos(new String[]{"x", "-other"});
        assertTrue(sniffed.getFlag("other").isSet(), "a line nothing scanned still sniffs any name");
    }
}
