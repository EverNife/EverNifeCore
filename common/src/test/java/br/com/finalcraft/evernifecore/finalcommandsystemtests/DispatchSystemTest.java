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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
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
    @TempDir(cleanup = CleanupMode.NEVER)
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
    // B1 - empty args with subcommands: FULL sends help, EXCEPT_EMPTY doesn't (but "help" still
    // does), NONE never sends help
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b1exceptempty", useDefaultHelp = CMDHelpType.EXCEPT_EMPTY)
    public static class B1_ExceptEmptyCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "b1full2", useDefaultHelp = CMDHelpType.FULL)
    public static class B1_FullCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "b1none", useDefaultHelp = CMDHelpType.NONE)
    public static class B1_NoneCmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void fullHelpTypeSendsHelpOnEmptyArgs() {
        FinalCMDPluginCommand command = newHarness().register(new B1_FullCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "");

        assertTrue(sentHelp(sender), "FULL should send the help on empty args");
    }

    @Test
    void exceptEmptyDoesNotSendHelpOnEmptyArgsButDoesOnHelp() {
        FinalCMDPluginCommand command = newHarness().register(new B1_ExceptEmptyCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "");
        assertFalse(sentHelp(sender), "EXCEPT_EMPTY should NOT send help on empty args");

        sender.clearMessages();
        harness.dispatch(command, sender, "help");
        assertTrue(sentHelp(sender), "EXCEPT_EMPTY should still send help on an explicit 'help'");
    }

    @Test
    void noneNeverSendsHelp() {
        FinalCMDPluginCommand command = newHarness().register(new B1_NoneCmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "");
        assertFalse(sentHelp(sender), "NONE should never send help on empty args");

        sender.clearMessages();
        harness.dispatch(command, sender, "help");
        assertFalse(sentHelp(sender), "NONE should never send help, not even on explicit 'help'");
    }

    // ------------------------------------------------------------------
    // B2 - "help", "?", "ajuda" all route to the help
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b2cmd")
    public static class B2_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void helpQuestionMarkAndAjudaAllRouteToHelp() {
        FinalCMDPluginCommand command = newHarness().register(new B2_Cmd());

        for (String helpToken : new String[]{"help", "?", "ajuda"}) {
            TestCommandSender sender = new TestCommandSender("console");
            harness.dispatch(command, sender, helpToken);
            assertTrue(sentHelp(sender), "'" + helpToken + "' should have routed to the help");
        }
    }

    // ------------------------------------------------------------------
    // B3 - subcommand resolution is case-insensitive and matches any of its declared labels
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b3cmd")
    public static class B3_Cmd {
        static String lastInvokedVia;

        @FinalCMD.SubCMD(subcmd = {"set", "s"})
        public void set(FCommandSender sender) {
            lastInvokedVia = "set";
        }
    }

    @Test
    void subCommandLookupIsCaseInsensitiveAndAcceptsAnyDeclaredLabel() {
        FinalCMDPluginCommand command = newHarness().register(new B3_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        B3_Cmd.lastInvokedVia = null;
        harness.dispatch(command, sender, "SeT");
        assertEquals("set", B3_Cmd.lastInvokedVia);

        B3_Cmd.lastInvokedVia = null;
        harness.dispatch(command, sender, "s");
        assertEquals("set", B3_Cmd.lastInvokedVia);
    }

    // ------------------------------------------------------------------
    // B4 - command-level permission denied -> permission message, method not invoked
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b4cmd", permission = "b4.perm")
    public static class B4_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            invoked = true;
        }
    }

    @Test
    void commandPermissionDeniedSendsMessageAndSkipsInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new B4_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B4_Cmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(B4_Cmd.invoked);
        sender.assertAnyMessageContains("permission");
    }

    // ------------------------------------------------------------------
    // B5 - subcommand-level permission denied -> same treatment
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b5cmd")
    public static class B5_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub", permission = "b5.perm")
        public void sub(FCommandSender sender) {
            invoked = true;
        }
    }

    @Test
    void subCommandPermissionDeniedSendsMessageAndSkipsInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new B5_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B5_Cmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(B5_Cmd.invoked);
        sender.assertAnyMessageContains("permission");
    }

    // ------------------------------------------------------------------
    // B6 - a playerOnly command (contextual FPlayer arg) invoked by console says so. It is a different
    // sentence from a missing permission on purpose: no permission node would ever have granted it
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b6cmd")
    public static class B6_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FPlayer player) {
            invoked = true;
        }
    }

    @Test
    void playerOnlySubCommandInvokedByConsoleSaysItNeedsAPlayer() {
        FinalCMDPluginCommand command = newHarness().register(new B6_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B6_Cmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(B6_Cmd.invoked);
        sender.assertAnyMessageContains("Only a player");
        assertFalse(sender.anyMessageContains("permission"), "the reason is not a permission: " + sender.getMessages());
    }

    // ------------------------------------------------------------------
    // B7 - unknown subcommand, no main interpreter -> UNKNOWN_SUBCOMMAND naming what does exist
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b7cmd")
    public static class B7_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {}
    }

    @Test
    void unknownSubCommandWithoutMainInterpreterNamesTheAvailableOnes() {
        FinalCMDPluginCommand command = newHarness().register(new B7_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "totallyUnknown");

        sender.assertAnyMessageContains("totallyUnknown");
        sender.assertAnyMessageContains("sub");
    }

    // ------------------------------------------------------------------
    // B8 - unknown subcommand, WITH a main interpreter -> the main interpreter runs with the args
    // ------------------------------------------------------------------

    public static class B8_Cmd {
        static String lastArg;

        @FinalCMD(aliases = "b8cmd")
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
        FinalCMDPluginCommand command = newHarness().register(new B8_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B8_Cmd.lastArg = null;

        harness.dispatch(command, sender, "totallyUnknown");

        assertEquals("totallyUnknown", B8_Cmd.lastArg);
    }

    // ------------------------------------------------------------------
    // B9 - CMDAccessValidation.onPreCommandValidation == false blocks without a framework message
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

    @FinalCMD(aliases = "b9cmd")
    public static class B9_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub", validation = {DenyingValidation.class})
        public void sub(FCommandSender sender) {
            invoked = true;
        }
    }

    @Test
    void deniedAccessValidationBlocksSilently() {
        FinalCMDPluginCommand command = newHarness().register(new B9_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B9_Cmd.invoked = false;
        DenyingValidation.called = false;

        harness.dispatch(command, sender, "sub");

        assertTrue(DenyingValidation.called, "the validation itself should have run");
        assertFalse(B9_Cmd.invoked);
        sender.assertNoMessageSent();
    }

    // ------------------------------------------------------------------
    // B10 - an exception inside the method stops at the framework: the sender is told in their own
    // language, the log carries the command, the args and the stack, and nothing is rethrown at the
    // platform (which would answer the same thing a second time, in a sentence of its own)
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b10cmd")
    public static class B10_Cmd {
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void exceptionInsideTheMethodAnswersTheSenderAndIsLoggedWithItsStack() {
        FinalCMDPluginCommand command = newHarness().register(new B10_Cmd());
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
        assertTrue(logged.contains("b10cmd") && logged.contains("sub"), "the log should mention the command/args: " + logged);
        assertTrue(logged.contains("IllegalStateException") && logged.contains("boom"), "the log should carry the cause: " + logged);
    }

    // ------------------------------------------------------------------
    // B11 - a declaration beats the help interceptor: a child, or a capture, named by a reserved word
    // is reachable, and the word still opens the help wherever nothing claims it
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "b11cmd")
    public static class B11_Cmd {
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

    @FinalCMD(aliases = "b11capturecmd")
    public static class B11_CaptureCmd {
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
        FinalCMDPluginCommand command = newHarness().register(new B11_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B11_Cmd.lastInvoked = null;

        harness.dispatch(command, sender, "help");

        assertEquals("help-sub", B11_Cmd.lastInvoked, "the declared child answers the word it declared");
        assertFalse(sentHelp(sender), "the interceptor did not shadow it");
    }

    @Test
    void aReservedWordNoChildClaimsStillOpensTheHelp() {
        FinalCMDPluginCommand command = newHarness().register(new B11_Cmd());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "ajuda");

        assertTrue(sentHelp(sender), "nothing declares 'ajuda', so it is still the help word");
    }

    @Test
    void aCaptureTakesAReservedWordAsItsOwnToken() {
        FinalCMDPluginCommand command = newHarness().register(new B11_CaptureCmd());
        B11_CaptureCmd.captured = null;

        harness.dispatch(command, new TestCommandSender("console"), "user Ajuda info");

        assertEquals("Ajuda", B11_CaptureCmd.captured, "a player named like a help word is addressable");
    }

    // ------------------------------------------------------------------
    // B12 - a flag declared by the executable of a node WITH children is typeable: reaching a node
    // that recognizes the flag ends the walk, instead of refusing the token as too early
    // ------------------------------------------------------------------

    public static class B12_Cmd {
        static Boolean rootVerbose;
        static Boolean nodeDetails;

        @FinalCMD(aliases = "b12cmd")
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
        FinalCMDPluginCommand command = newHarness().register(new B12_Cmd());
        B12_Cmd.rootVerbose = null;

        harness.dispatch(command, new TestCommandSender("console"), "--verbose");

        assertEquals(Boolean.TRUE, B12_Cmd.rootVerbose);
    }

    @Test
    void aNodeExecutableFlagIsTypeableEvenWhenTheNodeHasChildren() {
        FinalCMDPluginCommand command = newHarness().register(new B12_Cmd());
        B12_Cmd.nodeDetails = null;

        harness.dispatch(command, new TestCommandSender("console"), "admin --details");

        assertEquals(Boolean.TRUE, B12_Cmd.nodeDetails);
    }

    @Test
    void aFlagNoOneOnThePathDeclaresIsStillRefused() {
        FinalCMDPluginCommand command = newHarness().register(new B12_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        B12_Cmd.nodeDetails = null;

        harness.dispatch(command, sender, "admin --nosuchflag");

        assertNull(B12_Cmd.nodeDetails);
        assertTrue(sender.anyMessageContains("--nosuchflag"), "the refusal names the token: " + sender.getMessages());
    }
}
