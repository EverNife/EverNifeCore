package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A command that declares {@code flags = false} gets its line verbatim: a dashed word is a word, the bare
 * {@code --} is a token like any other, and a flag it declares anyway is refused at
 * registration (see {@code CommandShapeErrors}).
 */
class FlagFreeCommandSystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("FlagFree", tempDir);
        return harness;
    }

    @FinalCMD(aliases = "freefilter", flags = false)
    public static class WholeCommand_Cmd {
        static List<String> words;
        static String first;

        @FinalCMD.SubCMD(subcmd = "add")
        public void add(FCommandSender sender, @Arg("<first>") String first, @Arg("[rest...]") List<String> rest) {
            WholeCommand_Cmd.first = first;
            WholeCommand_Cmd.words = rest;
        }

        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender, @Arg("<word>") String word, @Arg(value = "<mode>", context = "fast|slow") String mode) {
            WholeCommand_Cmd.first = word + ":" + mode;
        }
    }

    /** A dashed word the walker meets before any method: at the root, and inside a node's capture. */
    public static class PathCmd {
        static String received;

        @FinalCMD(aliases = "freepath", flags = false)
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
    public static class OneSubcommand_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "add", flags = false)
        public void add(FCommandSender sender, @Arg("<word>") String word) {
            received = word;
        }

        @FinalCMD.SubCMD(subcmd = "list")
        public void list(FCommandSender sender, @Arg.Flag("--all") Boolean all) {
            received = String.valueOf(all);
        }
    }

    @Test
    void everyTokenIsPositionalTheBareDoubleDashIncluded() {
        FinalCMDPluginCommand command = newHarness().register(new WholeCommand_Cmd());
        WholeCommand_Cmd.words = null;

        harness.dispatch(command, new TestCommandSender("console"), "add -zombie -- --foo");

        assertEquals("-zombie", WholeCommand_Cmd.first);
        assertEquals(List.of("--", "--foo"), WholeCommand_Cmd.words);
    }

    @Test
    void theTabCountsADashedWordAsAPositional() {
        FinalCMDPluginCommand command = newHarness().register(new WholeCommand_Cmd());

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
        harness.dispatch(command, sender, "user -steve info");
        assertEquals("info:-steve", PathCmd.received, "a capture eats a dashed word like any other");
    }

    @Test
    void onlyTheSubcommandThatTurnedFlagsOffReadsADashedWord() {
        FinalCMDPluginCommand command = newHarness().register(new OneSubcommand_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        OneSubcommand_Cmd.received = null;
        harness.dispatch(command, sender, "add -foo");
        assertEquals("-foo", OneSubcommand_Cmd.received);

        OneSubcommand_Cmd.received = null;
        harness.dispatch(command, sender, "list --all");
        assertEquals("true", OneSubcommand_Cmd.received, "the sibling keeps its flags");
    }
}
