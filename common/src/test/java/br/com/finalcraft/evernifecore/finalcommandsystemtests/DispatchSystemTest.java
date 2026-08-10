package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code FCDefaultExecutor.onCommand}: help routing, sub-command resolution,
 * permission gates, playerOnly, unknown-subcommand fallbacks, {@link CMDAccessValidation}, and
 * exception propagation.
 */
class DispatchSystemTest {

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
        harness = new FinalCmdTestHarness("Dispatch", tempDir);
        return harness;
    }

    //the help footer (HelpContext.HOLD_MOUSE_OVER's EN_US text) is only ever sent by HelpContext.sendTo -
    //a marker that distinguishes "the help was sent" from any other message (e.g. PARAMETER_ERROR,
    //also sent on an unresolved subcommand name, which "" and "help" both are when help is skipped)
    private static boolean sentHelp(TestCommandSender sender) {
        return sender.anyMessageContains("Move the mouse over the commands");
    }

    // ------------------------------------------------------------------
    // empty args with subcommands: FULL sends help, EXCEPT_EMPTY doesn't (but "help" still
    // does), NONE never sends help
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "helpexceptempty", useDefaultHelp = CMDHelpType.EXCEPT_EMPTY)
    public static class ExceptEmptyHelpCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "helpfull", useDefaultHelp = CMDHelpType.FULL)
    public static class FullHelpCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "helpnone", useDefaultHelp = CMDHelpType.NONE)
    public static class NoHelpCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void fullHelpTypeSendsHelpOnEmptyArgs() {
        FinalCMDPluginCommand command = newHarness().register(new FullHelpCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "");

        assertTrue(sentHelp(sender), "FULL should send the help on empty args");
    }

    @Test
    void exceptEmptyDoesNotSendHelpOnEmptyArgsButDoesOnHelp() {
        FinalCMDPluginCommand command = newHarness().register(new ExceptEmptyHelpCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "");
        assertFalse(sentHelp(sender), "EXCEPT_EMPTY should NOT send help on empty args");

        sender.clearMessages();
        harness.dispatch(command, sender, "help");
        assertTrue(sentHelp(sender), "EXCEPT_EMPTY should still send help on an explicit 'help'");
    }

    @Test
    void noneNeverSendsHelp() {
        FinalCMDPluginCommand command = newHarness().register(new NoHelpCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "");
        assertFalse(sentHelp(sender), "NONE should never send help on empty args");

        sender.clearMessages();
        harness.dispatch(command, sender, "help");
        assertFalse(sentHelp(sender), "NONE should never send help, not even on explicit 'help'");
    }

    // ------------------------------------------------------------------
    // "help", "?", "ajuda" all route to the help
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "helpwords")
    public static class HelpWordsCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void helpQuestionMarkAndAjudaAllRouteToHelp() {
        FinalCMDPluginCommand command = newHarness().register(new HelpWordsCmd());

        for (String helpToken : new String[]{"help", "?", "ajuda"}) {
            TestCommandSender sender = new TestCommandSender("console");
            harness.dispatch(command, sender, helpToken);
            assertTrue(sentHelp(sender), "'" + helpToken + "' should have routed to the help");
        }
    }

    // ------------------------------------------------------------------
    // subcommand resolution is case-insensitive and matches any of its declared labels
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "sublabels")
    public static class SubCommandLabelsCmd {
        static String lastInvokedVia;

        @FinalCMD.SubCMD(subcmd = {"set", "s"})
        public void set(FCommandSender sender) {
            lastInvokedVia = "set";
        }
    }

    @Test
    void subCommandLookupIsCaseInsensitiveAndAcceptsAnyDeclaredLabel() {
        FinalCMDPluginCommand command = newHarness().register(new SubCommandLabelsCmd());
        TestCommandSender sender = new TestCommandSender("console");

        SubCommandLabelsCmd.lastInvokedVia = null;
        harness.dispatch(command, sender, "SeT");
        assertEquals("set", SubCommandLabelsCmd.lastInvokedVia);

        SubCommandLabelsCmd.lastInvokedVia = null;
        harness.dispatch(command, sender, "s");
        assertEquals("set", SubCommandLabelsCmd.lastInvokedVia);
    }

    // ------------------------------------------------------------------
    // command-level permission denied -> permission message, method not invoked
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "cmdperm", permission = "cmdperm.use")
    public static class CommandPermissionCmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            invoked = true;
        }
    }

    @Test
    void commandPermissionDeniedSendsMessageAndSkipsInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new CommandPermissionCmd());
        TestCommandSender sender = new TestCommandSender("console");
        CommandPermissionCmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(CommandPermissionCmd.invoked);
        sender.assertAnyMessageContains("permission");
    }

    // ------------------------------------------------------------------
    // subcommand-level permission denied -> same treatment
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "subperm")
    public static class SubCommandPermissionCmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub", permission = "subperm.use")
        public void sub(FCommandSender sender) {
            invoked = true;
        }
    }

    @Test
    void subCommandPermissionDeniedSendsMessageAndSkipsInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new SubCommandPermissionCmd());
        TestCommandSender sender = new TestCommandSender("console");
        SubCommandPermissionCmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(SubCommandPermissionCmd.invoked);
        sender.assertAnyMessageContains("permission");
    }

    // ------------------------------------------------------------------
    // a playerOnly command (contextual FPlayer arg) invoked by console says so. It is a different
    // sentence from a missing permission on purpose: no permission node would ever have granted it
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "playeronly")
    public static class PlayerOnlyCmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FPlayer player) {
            invoked = true;
        }
    }

    @Test
    void playerOnlySubCommandInvokedByConsoleSaysItNeedsAPlayer() {
        FinalCMDPluginCommand command = newHarness().register(new PlayerOnlyCmd());
        TestCommandSender sender = new TestCommandSender("console");
        PlayerOnlyCmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(PlayerOnlyCmd.invoked);
        sender.assertAnyMessageContains("Only a player");
        assertFalse(sender.anyMessageContains("permission"), "the reason is not a permission: " + sender.getMessages());
    }

    // ------------------------------------------------------------------
    // unknown subcommand, no main interpreter -> UNKNOWN_SUBCOMMAND naming what does exist
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "unknownsubnomain")
    public static class UnknownSubWithoutMainCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void unknownSubCommandWithoutMainInterpreterNamesTheAvailableOnes() {
        FinalCMDPluginCommand command = newHarness().register(new UnknownSubWithoutMainCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "totallyUnknown");

        sender.assertAnyMessageContains("totallyUnknown");
        sender.assertAnyMessageContains("sub");
    }

    // ------------------------------------------------------------------
    // unknown subcommand, WITH a main interpreter -> the main interpreter runs with the args
    // ------------------------------------------------------------------

    public static class UnknownSubWithMainCmd {
        static String lastArg;

        @FinalCMD(aliases = "unknownsubmain")
        //an unannotated String param binds to the CONTEXTUAL label parser instead of a positional
        //arg (String has both a value ArgParser and a contextual one registered globally) - @Arg is
        //what makes it read from args[0]
        public void main(FCommandSender sender, @Arg("[arg]") String firstArg) {
            lastArg = firstArg;
        }

        @FinalCMD.SubCMD(subcmd = "knownsub")
        public void knownsub(FCommandSender sender) {}
    }

    @Test
    void unknownSubCommandWithMainInterpreterInvokesTheMainInterpreterWithTheArgs() {
        FinalCMDPluginCommand command = newHarness().register(new UnknownSubWithMainCmd());
        TestCommandSender sender = new TestCommandSender("console");
        UnknownSubWithMainCmd.lastArg = null;

        harness.dispatch(command, sender, "totallyUnknown");

        assertEquals("totallyUnknown", UnknownSubWithMainCmd.lastArg);
    }

    // ------------------------------------------------------------------
    // CMDAccessValidation.onPreCommandValidation == false blocks without a framework message
    // ------------------------------------------------------------------

    public static class DenyingValidation extends CMDAccessValidation {
        static boolean called = false;

        public DenyingValidation() {
        }

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            called = true;
            return false;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            return true;
        }
    }

    @FinalCMD(aliases = "deniedvalidation")
    public static class DeniedValidationCmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub", validation = {DenyingValidation.class})
        public void sub(FCommandSender sender) {
            invoked = true;
        }
    }

    @Test
    void deniedAccessValidationBlocksSilently() {
        FinalCMDPluginCommand command = newHarness().register(new DeniedValidationCmd());
        TestCommandSender sender = new TestCommandSender("console");
        DeniedValidationCmd.invoked = false;
        DenyingValidation.called = false;

        harness.dispatch(command, sender, "sub");

        assertTrue(DenyingValidation.called, "the validation itself should have run");
        assertFalse(DeniedValidationCmd.invoked);
        sender.assertNoMessageSent();
    }

    // ------------------------------------------------------------------
    // an exception inside the method stops at the framework: the sender is told in their own
    // language, the log carries the command, the args and the stack, and nothing is rethrown at the
    // platform (which would answer the same thing a second time, in a sentence of its own)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "throwing")
    public static class ThrowingCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void exceptionInsideTheMethodAnswersTheSenderAndIsLoggedWithItsStack() {
        FinalCMDPluginCommand command = newHarness().register(new ThrowingCmd());
        TestCommandSender sender = new TestCommandSender("console");

        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(capturedOut));
        try {
            harness.dispatch(command, sender, "sub");
        } finally {
            System.setOut(originalOut);
        }

        sender.assertAnyMessageContains("went wrong");

        String logged = capturedOut.toString();
        assertTrue(logged.contains("SEVERE"), "the failure should have been logged severe: " + logged);
        assertTrue(logged.contains("throwing") && logged.contains("sub"), "the log should mention the command/args: " + logged);
        assertTrue(logged.contains("IllegalStateException") && logged.contains("boom"), "the log should carry the cause: " + logged);
    }

    // ------------------------------------------------------------------
    // a declaration beats the help interceptor: a child, or a capture, named by a reserved word
    // is reachable, and the word still opens the help wherever nothing claims it
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "reservedword")
    public static class ReservedWordSubCmd {
        static String lastInvoked;

        @FinalCMD.SubCMD(subcmd = "help")
        public void helpSub(FCommandSender sender) {
            lastInvoked = "help-sub";
        }

        @FinalCMD.SubCMD(subcmd = "other")
        public void other(FCommandSender sender) {
            lastInvoked = "other";
        }
    }

    @FinalCMD(aliases = "reservedwordcapture")
    public static class ReservedWordCaptureCmd {
        static String captured;

        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                captured = user;
                return user;
            }

            @FinalCMD.SubCMD(subcmd = "info")
            public void info(FCommandSender sender) {}
        }
    }

    @Test
    void aSubcommandNamedLikeAReservedWordIsReachable() {
        FinalCMDPluginCommand command = newHarness().register(new ReservedWordSubCmd());
        TestCommandSender sender = new TestCommandSender("console");
        ReservedWordSubCmd.lastInvoked = null;

        harness.dispatch(command, sender, "help");

        assertEquals("help-sub", ReservedWordSubCmd.lastInvoked, "the declared child answers the word it declared");
        assertFalse(sentHelp(sender), "the interceptor did not shadow it");
    }

    @Test
    void aReservedWordNoChildClaimsStillOpensTheHelp() {
        FinalCMDPluginCommand command = newHarness().register(new ReservedWordSubCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "ajuda");

        assertTrue(sentHelp(sender), "nothing declares 'ajuda', so it is still the help word");
    }

    @Test
    void aCaptureTakesAReservedWordAsItsOwnToken() {
        FinalCMDPluginCommand command = newHarness().register(new ReservedWordCaptureCmd());
        ReservedWordCaptureCmd.captured = null;

        harness.dispatch(command, new TestCommandSender("console"), "user Ajuda info");

        assertEquals("Ajuda", ReservedWordCaptureCmd.captured, "a player named like a help word is addressable");
    }

    // ------------------------------------------------------------------
    // a flag declared by the executable of a node WITH children is typeable: reaching a node
    // that recognizes the flag ends the walk, instead of refusing the token as too early
    // ------------------------------------------------------------------

    public static class NodeFlagCmd {
        static Boolean rootVerbose;
        static Boolean nodeDetails;

        @FinalCMD(aliases = "nodeflag")
        public void root(FCommandSender sender, @Arg.Flag("--verbose") Boolean verbose) {
            rootVerbose = verbose;
        }

        @FinalCMD.Node(subcmd = "admin")
        public static class AdminNode {
            @FinalCMD.Execute
            public void run(FCommandSender sender, @Arg.Flag("--details") Boolean details) {
                nodeDetails = details;
            }

            @FinalCMD.SubCMD(subcmd = "ban")
            public void ban(FCommandSender sender) {}
        }
    }

    @Test
    void aRootFlagIsTypeableEvenWhenTheRootHasChildren() {
        FinalCMDPluginCommand command = newHarness().register(new NodeFlagCmd());
        NodeFlagCmd.rootVerbose = null;

        harness.dispatch(command, new TestCommandSender("console"), "--verbose");

        assertEquals(Boolean.TRUE, NodeFlagCmd.rootVerbose);
    }

    @Test
    void aNodeExecutableFlagIsTypeableEvenWhenTheNodeHasChildren() {
        FinalCMDPluginCommand command = newHarness().register(new NodeFlagCmd());
        NodeFlagCmd.nodeDetails = null;

        harness.dispatch(command, new TestCommandSender("console"), "admin --details");

        assertEquals(Boolean.TRUE, NodeFlagCmd.nodeDetails);
    }

    @Test
    void aFlagNoOneOnThePathDeclaresIsStillRefused() {
        FinalCMDPluginCommand command = newHarness().register(new NodeFlagCmd());
        TestCommandSender sender = new TestCommandSender("console");
        NodeFlagCmd.nodeDetails = null;

        harness.dispatch(command, sender, "admin --nosuchflag");

        assertNull(NodeFlagCmd.nodeDetails);
        assertTrue(sender.anyMessageContains("--nosuchflag"), "the refusal names the token: " + sender.getMessages());
    }
}
