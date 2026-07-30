package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tree end to end: how it is mounted, how a line walks it, what each capture eats and what the
 * leaf at the end receives.
 */
class CommandTreeSystemTest {

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
        harness = new FinalCmdTestHarness("Tree", tempDir);
        return harness;
    }

    /** Shape only: label, how many tokens the node eats, whether it runs something, and its children. */
    private static String describeChildren(CommandNode node) {
        StringBuilder builder = new StringBuilder();
        for (CommandNode child : node.getChildren()) {
            describe(child, builder, 0);
        }
        return builder.toString();
    }

    private static void describe(CommandNode node, StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(node.getPrimaryLabel())
                .append(node.getCapture() == null ? "" : "(" + node.getCapture().tokenWidth() + ")")
                .append(node.getExecutable() == null ? "" : "*")
                .append(" perm=").append(node.getCmdData().getPermission())
                .append("\n");
        for (CommandNode child : node.getChildren()) {
            describe(child, builder, depth + 1);
        }
    }

    // ------------------------------------------------------------------
    // The two mount forms are one semantics: the same content class, mounted as an inner class or
    // through a field, has to produce the very same tree
    // ------------------------------------------------------------------

    public static class UserContent {
        static String seenTarget;
        static String seenNode;

        @FinalCMD.Capture
        public String capture(@Arg("<user>") String user) {
            return user;
        }

        @FinalCMD.SubCMD(subcmd = "info")
        public void info(FCommandSender sender, @Arg.NodeCaptured String target) {
            seenTarget = target;
        }

        @FinalCMD.Node(subcmd = "permission")
        public static class UserPermissionNode {
            @FinalCMD.SubCMD(subcmd = "set")
            public void set(FCommandSender sender, @Arg.NodeCaptured String target, @Arg("<node>") String node) {
                seenTarget = target;
                seenNode = node;
            }
        }
    }

    @FinalCMD(aliases = "formacmd")
    public static class FormA_Cmd {
        @FinalCMD.Node(subcmd = "user", permission = "tree.user")
        public static class InnerUserNode extends UserContent {
        }
    }

    @FinalCMD(aliases = "formdcmd")
    public static class FormD_Cmd {
        @FinalCMD.Node(subcmd = "user", permission = "tree.user")
        private final UserContent user = new UserContent();
    }

    @Test
    void formAAndFormDProduceTheSameTree() {
        FinalCMDPluginCommand formA = newHarness().register(new FormA_Cmd());
        FinalCMDPluginCommand formD = harness.register(new FormD_Cmd());

        assertEquals(describeChildren(formA.getRoot()), describeChildren(formD.getRoot()));
        assertEquals("user(1) perm=tree.user\n  info* perm=\n  permission perm=\n    set* perm=\n",
                describeChildren(formA.getRoot()));
    }

    @Test
    void bothMountFormsDispatchTheSameWay() {
        FinalCMDPluginCommand formA = newHarness().register(new FormA_Cmd());
        FinalCMDPluginCommand formD = harness.register(new FormD_Cmd());
        TestCommandSender sender = new TestCommandSender("console").grant("tree.user");

        UserContent.seenTarget = null;
        harness.dispatch(formA, sender, "user Steve permission set fly");
        assertEquals("Steve", UserContent.seenTarget);
        assertEquals("fly", UserContent.seenNode);

        UserContent.seenTarget = null;
        harness.dispatch(formD, sender, "user Alex permission set build");
        assertEquals("Alex", UserContent.seenTarget);
        assertEquals("build", UserContent.seenNode);
    }

    // ------------------------------------------------------------------
    // The same class mounted twice, each mount with its own label and permission - the whole point of
    // declaring the segment at the mount point instead of on the class
    // ------------------------------------------------------------------

    public static class ArenaContent {
        static String lastModule;

        private final String module;

        public ArenaContent(String module) {
            this.module = module;
        }

        @FinalCMD.SubCMD(subcmd = "enable")
        public void enable(FCommandSender sender) {
            lastModule = module;
        }
    }

    @FinalCMD(aliases = "arenacmd")
    public static class TwiceMounted_Cmd {
        @FinalCMD.Node(subcmd = "duel", permission = "arena.duel")
        private final ArenaContent duel = new ArenaContent("DUEL");

        @FinalCMD.Node(subcmd = "ffa", permission = "arena.ffa")
        private final ArenaContent ffa = new ArenaContent("FFA");
    }

    @Test
    void theSameClassMountedTwiceKeepsItsOwnLabelsPermissionsAndState() {
        FinalCMDPluginCommand command = newHarness().register(new TwiceMounted_Cmd());

        assertEquals("duel perm=arena.duel\n  enable* perm=\nffa perm=arena.ffa\n  enable* perm=\n",
                describeChildren(command.getRoot()));

        TestCommandSender allowed = new TestCommandSender("console").grant("arena.duel").grant("arena.ffa");
        harness.dispatch(command, allowed, "duel enable");
        assertEquals("DUEL", ArenaContent.lastModule);
        harness.dispatch(command, allowed, "ffa enable");
        assertEquals("FFA", ArenaContent.lastModule);

        ArenaContent.lastModule = null;
        TestCommandSender duelOnly = new TestCommandSender("console").grant("arena.duel");
        harness.dispatch(command, duelOnly, "ffa enable");
        assertNull(ArenaContent.lastModule, "the ffa mount has a permission of its own");
    }

    // ------------------------------------------------------------------
    // A tree deep enough to answer at levels 1 to 4, with and without a capture at each level
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "deepcmd")
    public static class Deep_Cmd {
        static final List<String> CALLS = new ArrayList<>();
        static final List<String> CAPTURES = new ArrayList<>();
        static final List<String> TOKENS = new ArrayList<>();

        @FinalCMD.SubCMD(subcmd = "one")
        public void one(FCommandSender sender) {
            CALLS.add("one");
        }

        @FinalCMD.Node(subcmd = "nc")
        public static class NoCaptureNode {
            @FinalCMD.SubCMD(subcmd = "two")
            public void two(FCommandSender sender) {
                CALLS.add("nc.two");
            }

            @FinalCMD.Node(subcmd = "mid")
            public static class NoCaptureMidNode {
                @FinalCMD.SubCMD(subcmd = "three")
                public void three(FCommandSender sender) {
                    CALLS.add("nc.mid.three");
                }

                @FinalCMD.Node(subcmd = "bottom")
                public static class NoCaptureBottomNode {
                    @FinalCMD.SubCMD(subcmd = "four")
                    public void four(FCommandSender sender) {
                        CALLS.add("nc.mid.bottom.four");
                    }
                }
            }
        }

        @FinalCMD.Node(subcmd = "cap")
        public static class CaptureNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                CAPTURES.add("cap=" + user);
                return user;
            }

            @FinalCMD.SubCMD(subcmd = "two")
            public void two(FCommandSender sender, @Arg.NodeCaptured String user, @Arg.Flag("--force") Boolean force) {
                CALLS.add("cap.two:" + user + ":" + force);
            }

            @FinalCMD.Node(subcmd = "mid")
            public static class CaptureMidNode {
                @FinalCMD.SubCMD(subcmd = "three")
                public void three(FCommandSender sender, @Arg.NodeCaptured String user) {
                    CALLS.add("cap.mid.three:" + user);
                }

                @FinalCMD.Node(subcmd = "pair")
                public static class PairCaptureNode {
                    @FinalCMD.Capture
                    public String capture(@Arg("<server>") String server, @Arg("<world>") String world) {
                        CAPTURES.add("pair=" + server + "/" + world);
                        return server + "/" + world;
                    }

                    //The same leaf takes both forms: the capture's own return value, and the individual
                    //tokens of a two-token capture and of a one-token one
                    @FinalCMD.SubCMD(subcmd = "four")
                    public void four(FCommandSender sender,
                                     @Arg.NodeCaptured("cap") String user,
                                     @Arg.NodeCaptured("cap.mid.pair") String place,
                                     @Arg.NodeCaptured("cap:<user>") String userToken,
                                     @Arg.NodeCaptured("cap.mid.pair:<server>") String server,
                                     @Arg.NodeCaptured("cap.mid.pair:<world>") String world) {
                        CALLS.add("cap.mid.pair.four:" + user + "@" + place);
                        TOKENS.add(userToken + "|" + server + "|" + world);
                    }
                }
            }
        }
    }

    private FinalCMDPluginCommand freshDeepCommand() {
        FinalCMDPluginCommand command = newHarness().register(new Deep_Cmd());
        Deep_Cmd.CALLS.clear();
        Deep_Cmd.CAPTURES.clear();
        Deep_Cmd.TOKENS.clear();
        return command;
    }

    @Test
    void dispatchReachesDepthOneToFourWithAndWithoutCaptures() {
        FinalCMDPluginCommand command = freshDeepCommand();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "one");
        harness.dispatch(command, sender, "nc two");
        harness.dispatch(command, sender, "nc mid three");
        harness.dispatch(command, sender, "nc mid bottom four");
        harness.dispatch(command, sender, "cap Steve two");
        harness.dispatch(command, sender, "cap Steve mid three");
        harness.dispatch(command, sender, "cap Steve mid pair survival nether four");

        assertEquals(Arrays.asList(
                "one",
                "nc.two",
                "nc.mid.three",
                "nc.mid.bottom.four",
                "cap.two:Steve:null",
                "cap.mid.three:Steve",
                "cap.mid.pair.four:Steve@survival/nether"
        ), Deep_Cmd.CALLS);
    }

    @Test
    void aTwoTokenCaptureInTheMiddleOfThePathEatsExactlyTwoTokens() {
        FinalCMDPluginCommand command = freshDeepCommand();

        harness.dispatch(command, new TestCommandSender("console"), "cap Steve mid pair survival nether four");

        assertEquals(Arrays.asList("cap=Steve", "pair=survival/nether"), Deep_Cmd.CAPTURES);
        assertEquals(Arrays.asList("cap.mid.pair.four:Steve@survival/nether"), Deep_Cmd.CALLS);
    }

    @Test
    void aCapturesIndividualArgReachesTheLeafByNameAlongsideTheWholeCapture() {
        FinalCMDPluginCommand command = freshDeepCommand();

        harness.dispatch(command, new TestCommandSender("console"), "cap Steve mid pair survival nether four");

        //Each token of the two-token capture lands on its own parameter, and so does the single token
        //of the one-token capture above it
        assertEquals(Arrays.asList("Steve|survival|nether"), Deep_Cmd.TOKENS);
        //and the plain node-path form on the same leaf still hands over what each @Capture returned
        assertEquals(Arrays.asList("cap.mid.pair.four:Steve@survival/nether"), Deep_Cmd.CALLS);
    }

    @Test
    void aCaptureEatsATokenEvenWhenItSpellsAChildsLabel() {
        FinalCMDPluginCommand command = freshDeepCommand();

        //"two" is both a legal user name and the label of a child of this node - the capture eats first
        harness.dispatch(command, new TestCommandSender("console"), "cap two two");

        assertEquals(Arrays.asList("cap=two"), Deep_Cmd.CAPTURES);
        assertEquals(Arrays.asList("cap.two:two:null"), Deep_Cmd.CALLS);
    }

    @Test
    void aFlagInTheMiddleOfThePathIsRefusedBeforeAnyCaptureRuns() {
        FinalCMDPluginCommand command = freshDeepCommand();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "cap --force Steve two");

        assertTrue(Deep_Cmd.CAPTURES.isEmpty(), "the traversal refuses before it ever runs a capture, so nothing was captured");
        assertTrue(Deep_Cmd.CALLS.isEmpty());
        sender.assertAnyMessageContains("--force");

        //the very same flag, written after the path, is fine
        harness.dispatch(command, sender, "cap Steve two --force");
        assertEquals(Arrays.asList("cap.two:Steve:true"), Deep_Cmd.CALLS);
    }

    @Test
    void aPathEndingOnANodePrintsThatNodesHelpAndAnUnknownWordNamesTheChildren() {
        FinalCMDPluginCommand command = freshDeepCommand();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "nc mid");
        assertTrue(sender.anyMessageContains("Move the mouse over the commands"), sender.getMessages().toString());
        sender.assertAnyMessageContains("nc mid three");
        assertTrue(Deep_Cmd.CALLS.isEmpty());

        sender.clearMessages();
        harness.dispatch(command, sender, "nc mid nosuchthing");
        sender.assertAnyMessageContains("nosuchthing");
        sender.assertAnyMessageContains("three");
    }

    @Test
    void tabAnswersWithTheChildrenOfTheNodeReachedAndNothingForAPathThatMatchesNobody() {
        FinalCMDPluginCommand command = freshDeepCommand();
        TestCommandSender sender = new TestCommandSender("console");

        assertEquals(Arrays.asList("bottom", "three"), harness.tab(command, sender, "nc", "mid", ""));
        assertEquals(Arrays.asList("two"), harness.tab(command, sender, "cap", "Steve", "t"));
        assertTrue(harness.tab(command, sender, "nc", "nosuchthing", "").isEmpty());
    }

    // ------------------------------------------------------------------
    // Two ancestors capturing the same type: naming them is what disambiguates, and leaving them
    // unnamed is a registration error (see CommandTreeRegistrationErrorsTest)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "diffcmd")
    public static class Diff_Cmd {
        static String left;
        static String right;

        @FinalCMD.Node(subcmd = "user")
        public static class DiffUserNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                return user;
            }

            @FinalCMD.Node(subcmd = "compare")
            public static class DiffCompareNode {
                @FinalCMD.Capture
                public String capture(@Arg("<other>") String other) {
                    return other;
                }

                @FinalCMD.SubCMD(subcmd = "diff")
                public void diff(FCommandSender sender,
                                 @Arg.NodeCaptured("user") String a,
                                 @Arg.NodeCaptured("user.compare") String b) {
                    left = a;
                    right = b;
                }
            }
        }
    }

    @Test
    void namedCapturedDeliversEachAncestorsOwnValue() {
        FinalCMDPluginCommand command = newHarness().register(new Diff_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "user Steve compare Alex diff");

        assertEquals("Steve", Diff_Cmd.left);
        assertEquals("Alex", Diff_Cmd.right);
    }

    // ------------------------------------------------------------------
    // A node that runs something of its own, in the restricted form: no @Arg, so every token after it
    // still has to be a child's label
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "execcmd")
    public static class Execute_Cmd {
        static final List<String> CALLS = new ArrayList<>();

        @FinalCMD.Node(subcmd = "acct")
        public static class AccountNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                return user;
            }

            @FinalCMD.Execute
            public void show(FCommandSender sender, @Arg.NodeCaptured String user) {
                CALLS.add("show:" + user);
            }

            @FinalCMD.SubCMD(subcmd = "info")
            public void info(FCommandSender sender, @Arg.NodeCaptured String user) {
                CALLS.add("info:" + user);
            }
        }
    }

    @Test
    void anExecutableNodeRunsItselfAndStillRoutesToItsChildren() {
        FinalCMDPluginCommand command = newHarness().register(new Execute_Cmd());
        Execute_Cmd.CALLS.clear();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "acct Steve");
        harness.dispatch(command, sender, "acct Steve info");

        assertEquals(Arrays.asList("show:Steve", "info:Steve"), Execute_Cmd.CALLS);
    }

    // ------------------------------------------------------------------
    // The variadic tail, in each of the six shapes it can be handed over in
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "tailcmd")
    public static class Tail_Cmd {
        static String asString;
        static String[] asArray;
        static Argumento asArgumento;
        static MultiArgumentos asMulti;
        static List<String> asList;
        static Set<String> asSet;

        @FinalCMD.SubCMD(subcmd = "list")
        public void list(FCommandSender sender, @Arg("<reason...>") List<String> reason) {
            asList = reason;
        }

        @FinalCMD.SubCMD(subcmd = "set")
        public void set(FCommandSender sender, @Arg("<reason...>") Set<String> reason) {
            asSet = reason;
        }

        @FinalCMD.SubCMD(subcmd = "str")
        public void str(FCommandSender sender, @Arg("<reason...>") String reason) {
            asString = reason;
        }

        @FinalCMD.SubCMD(subcmd = "arr")
        public void arr(FCommandSender sender, @Arg("<reason...>") String... reason) {
            asArray = reason;
        }

        @FinalCMD.SubCMD(subcmd = "arg")
        public void arg(FCommandSender sender, @Arg("<reason...>") Argumento reason) {
            asArgumento = reason;
        }

        @FinalCMD.SubCMD(subcmd = "multi")
        public void multi(FCommandSender sender, @Arg("<reason...>") MultiArgumentos reason) {
            asMulti = reason;
        }

        @FinalCMD.SubCMD(subcmd = "opt")
        public void opt(FCommandSender sender, @Arg("[reason...]") String reason) {
            asString = reason;
        }

        @FinalCMD.SubCMD(subcmd = "flagged")
        public void flagged(FCommandSender sender, @Arg.Flag("--loud") Boolean loud, @Arg("<reason...>") String reason) {
            asString = reason;
            asArray = new String[]{String.valueOf(loud)};
        }
    }

    @Test
    void theVariadicTailFillsEveryAcceptedType() {
        FinalCMDPluginCommand command = newHarness().register(new Tail_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "str griefou minha base");
        assertEquals("griefou minha base", Tail_Cmd.asString);

        harness.dispatch(command, sender, "arr griefou minha base");
        assertEquals(Arrays.asList("griefou", "minha", "base"), Arrays.asList(Tail_Cmd.asArray));

        harness.dispatch(command, sender, "arg griefou minha base");
        assertEquals("griefou minha base", Tail_Cmd.asArgumento.toString());

        harness.dispatch(command, sender, "multi griefou minha base");
        assertEquals(Arrays.asList("griefou", "minha", "base"), Tail_Cmd.asMulti.getStringArgs());

        harness.dispatch(command, sender, "list griefou minha base");
        assertEquals(Arrays.asList("griefou", "minha", "base"), Tail_Cmd.asList);
    }

    @Test
    void theVariadicTailAsASetKeepsTypingOrderAndDropsRepeats() {
        FinalCMDPluginCommand command = newHarness().register(new Tail_Cmd());

        harness.dispatch(command, new TestCommandSender("console"), "set base minha base");

        assertEquals(Arrays.asList("base", "minha"), new ArrayList<>(Tail_Cmd.asSet));
    }

    @Test
    void theVariadicTailHandlesOneThreeAndZeroRemainingTokens() {
        FinalCMDPluginCommand command = newHarness().register(new Tail_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "str alone");
        assertEquals("alone", Tail_Cmd.asString);

        harness.dispatch(command, sender, "str one two three");
        assertEquals("one two three", Tail_Cmd.asString);

        //a REQUIRED tail with nothing left is a missing argument, so the usage line is sent instead
        Tail_Cmd.asString = null;
        harness.dispatch(command, sender, "str");
        assertNull(Tail_Cmd.asString);
        sender.assertAnyMessageContains("<reason...>");

        //an OPTIONAL tail with nothing left is simply empty
        harness.dispatch(command, sender, "opt");
        assertEquals("", Tail_Cmd.asString);
    }

    @Test
    void theTailIsHandedOverExactlyAsTypedAndNeverScannedForFlags() {
        FinalCMDPluginCommand command = newHarness().register(new Tail_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        //From the first token of the tail on the line is somebody's sentence: a flag marker inside it is
        //their word, and so is the escape - nothing is removed, so nothing has to be escaped
        harness.dispatch(command, sender, "flagged hi -- --loud");
        assertEquals("hi -- --loud", Tail_Cmd.asString);
        assertEquals("null", Tail_Cmd.asArray[0], "a marker inside the tail is text, not the flag");

        //The flag is typed where the scan still runs: before the tail opens
        harness.dispatch(command, sender, "flagged --loud hi --loud");
        assertEquals("hi --loud", Tail_Cmd.asString, "only the marker BEFORE the tail was taken");
        assertEquals("true", Tail_Cmd.asArray[0]);
    }

    // ------------------------------------------------------------------
    // fromSender on a leaf: the same method serves the named target and the inferred one
    // ------------------------------------------------------------------

    public static class SenderNamedParser extends ArgParser<String> {
        public SenderNamedParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(@Nonnull ParseCall call) {
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public ParseResult<String> fromSender(@Nonnull ParseCall call) {
            return ParseResult.of("where:" + call.getSender().getName());
        }
    }

    @FinalCMD(aliases = "fromsendercmd")
    public static class FromSender_Cmd {
        static Boolean enabled;
        static String arena;

        @FinalCMD.SubCMD(subcmd = "enable")
        public void enable(FCommandSender sender,
                           @Arg("<True|False>") Boolean value,
                           @Arg(value = "<ArenaID>", fromSender = true, parser = SenderNamedParser.class) String target) {
            enabled = value;
            arena = target;
        }
    }

    @Test
    void fromSenderOnALeafAnswersWithAndWithoutTheToken() {
        FinalCMDPluginCommand command = newHarness().register(new FromSender_Cmd());
        TestCommandSender sender = new TestCommandSender("Admin");

        harness.dispatch(command, sender, "enable true myarena");
        assertEquals(Boolean.TRUE, FromSender_Cmd.enabled);
        assertEquals("myarena", FromSender_Cmd.arena);

        harness.dispatch(command, sender, "enable false");
        assertEquals(Boolean.FALSE, FromSender_Cmd.enabled);
        assertEquals("where:Admin", FromSender_Cmd.arena);
    }

    @Test
    void theWindowStartsAfterThePathSoAnArgIndexIsLocal() {
        FinalCMDPluginCommand command = freshDeepCommand();
        CommandNode capNode = command.getRoot().getChild("cap");

        assertNotNull(capNode);
        assertEquals(1, capNode.getCapture().tokenWidth());
        assertEquals("cap <user>", capNode.getUsagePath());
        assertFalse(capNode.getChildren().isEmpty());
    }
}
