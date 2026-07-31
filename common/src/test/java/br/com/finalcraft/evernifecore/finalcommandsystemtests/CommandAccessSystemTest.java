package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One access contract, three surfaces. Every node from the root down to the target, plus the
 * {@code @FinalCMD.Execute} declaration of the node that runs, decides alike whether the dispatch runs
 * it, whether the tab offers it and whether the help lists it.
 * <p>
 * The shape these tests exist to forbid is the worst of the three disagreeing: a tab that hides a
 * subcommand the dispatch happily executes, which reads to the developer as "protected".
 */
class CommandAccessSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void reset() {
        Deny.commandCalls = 0;
        ExecutePermissionCmd.ran = false;
        ExecuteValidationCmd.ran = false;
        RootValidationCmd.ran = false;
        GroupingNodeValidationCmd.ran = false;
        HelpContextInjectionCmd.received = null;
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Access", tempDir);
        return harness;
    }

    /** Refuses on both surfaces, and counts the run-time calls so a double evaluation shows up. */
    public static class Deny extends CMDAccessValidation {
        static int commandCalls;

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            commandCalls++;
            return false;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // @FinalCMD.Execute declares its own permission and validations, and nothing else answers for them
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "execpermcmd")
    public static class ExecutePermissionCmd {
        static boolean ran;

        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Execute(permission = "access.exec.admin")
            public void run(FCommandSender sender) {
                ran = true;
            }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @Test
    void executePermissionStopsTheDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new ExecutePermissionCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "node");

        assertFalse(ExecutePermissionCmd.ran, "@Execute(permission) must gate the dispatch, not only the tab");
        assertTrue(sender.getMessages().size() > 0, "a refused permission always says so");
    }

    @Test
    void executePermissionGrantedStillRuns() {
        FinalCMDPluginCommand command = newHarness().register(new ExecutePermissionCmd());
        TestCommandSender sender = Senders.console().grant("access.exec.admin");

        harness.dispatch(command, sender, "node");

        assertTrue(ExecutePermissionCmd.ran, "the permission is a gate, not a wall");
    }

    @FinalCMD(aliases = "execvalidationcmd")
    public static class ExecuteValidationCmd {
        static boolean ran;

        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Execute(validation = Deny.class)
            public void run(FCommandSender sender) {
                ran = true;
            }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @Test
    void executeValidationStopsTheDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new ExecuteValidationCmd());

        harness.dispatch(command, Senders.console(), "node");

        assertFalse(ExecuteValidationCmd.ran, "@Execute(validation) must gate the dispatch");
        assertEquals(1, Deny.commandCalls, "the validation runs once, not once per surface it is reachable from");
    }

    // ------------------------------------------------------------------
    // The root is a node of every path, not only of its own
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "rootvalidationcmd", validation = Deny.class)
    public static class RootValidationCmd {
        static boolean ran;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender) {
            ran = true;
        }
    }

    @Test
    void theRootValidationAlsoGuardsWhatIsBelowIt() {
        FinalCMDPluginCommand command = newHarness().register(new RootValidationCmd());

        harness.dispatch(command, Senders.console(), "sub");

        assertFalse(RootValidationCmd.ran, "a validation on the @FinalCMD guards the whole tree, not just its own method");
    }

    // ------------------------------------------------------------------
    // A branch that only holds children is still a declaration
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "nodevalidationcmd")
    public static class GroupingNodeValidationCmd {
        static boolean ran;

        @FinalCMD.Node(subcmd = "admin", validation = Deny.class)
        public static class AdminNode {
            @FinalCMD.SubCMD(subcmd = "ban")
            public void ban(FCommandSender sender) {
                ran = true;
            }
        }
    }

    @Test
    void aGroupingNodeValidationStopsTheDispatch() {
        FinalCMDPluginCommand command = newHarness().register(new GroupingNodeValidationCmd());

        harness.dispatch(command, Senders.console(), "admin ban");

        assertFalse(GroupingNodeValidationCmd.ran, "a node with no executable of its own still validates the path through it");
    }

    @Test
    void aGroupingNodeValidationHidesItFromTheTabToo() {
        FinalCMDPluginCommand command = newHarness().register(new GroupingNodeValidationCmd());

        List<String> suggestions = harness.tab(command, Senders.console(), "");

        assertFalse(suggestions.contains("admin"), "the tab must not offer what the dispatch refuses: " + suggestions);
    }

    // ------------------------------------------------------------------
    // The injected HelpContext belongs to the node that was reached
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "helpctxcmd")
    public static class HelpContextInjectionCmd {
        static HelpContext received;

        @FinalCMD.Node(subcmd = "branch")
        public static class BranchNode {
            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, HelpContext helpContext) {
                received = helpContext;
            }
        }
    }

    @Test
    void theInjectedHelpContextIsTheOneOfTheReachedNode() {
        FinalCMDPluginCommand command = newHarness().register(new HelpContextInjectionCmd());

        harness.dispatch(command, Senders.console(), "branch leaf");

        assertNotNull(HelpContextInjectionCmd.received, "the contextual HelpContext must be injected");
        assertEquals("branch", HelpContextInjectionCmd.received.getNode().getPrimaryLabel(),
                "a method four levels down must get its own help, not the root's");
    }

    // ------------------------------------------------------------------
    // The list a mistyped word gets is the fourth surface: it names exactly what the tab and the help
    // name, so a typo is not a way around a validation or a playerOnly
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "unknownsubcmd")
    public static class UnknownSubCmdListCmd {
        @FinalCMD.SubCMD(subcmd = "open")
        public void open(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "hidden", validation = Deny.class)
        public void hidden(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "mine")
        public void mine(FPlayer player) {}
    }

    @Test
    void theUnknownSubcommandListHidesWhatTheTabHides() {
        FinalCMDPluginCommand command = newHarness().register(new UnknownSubCmdListCmd());
        TestCommandSender console = Senders.console();

        harness.dispatch(command, console, "notASubcommand");

        assertTrue(console.anyMessageContains("open"), "a reachable subcommand is still named: " + console.getMessages());
        assertFalse(console.anyMessageContains("hidden"), "a validation that hides it from the tab hides it here too: " + console.getMessages());
        assertFalse(console.anyMessageContains("mine"), "a playerOnly subcommand is not named to the console: " + console.getMessages());
    }

    // ------------------------------------------------------------------
    // "You are not a player" and "you lack a permission" are different answers, and the help says the
    // one that is true - a console sent hunting for a node that would not have helped is worse than
    // being told plainly that it has to be a player
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "playeronlyhelpcmd")
    public static class PlayerOnlyHelpCmd {
        @FinalCMD.SubCMD(subcmd = "mine")
        public void mine(FPlayer player) {}

        @FinalCMD.SubCMD(subcmd = "yours")
        public void yours(FPlayer player) {}
    }

    @Test
    void aHelpWhoseEveryLineIsPlayerOnlySaysThatAndNotThePermission() {
        FinalCMDPluginCommand command = newHarness().register(new PlayerOnlyHelpCmd());
        TestCommandSender console = Senders.console();

        harness.dispatch(command, console, "help");

        assertTrue(console.anyMessageContains("Only a player"), "the honest reason: " + console.getMessages());
        assertFalse(console.anyMessageContains("permission"), "no permission would have opened it: " + console.getMessages());
    }

    @Test
    void thatSameHelpStillRendersForAPlayer() {
        FinalCMDPluginCommand command = newHarness().register(new PlayerOnlyHelpCmd());
        TestFPlayerSender player = Senders.player("Steve");

        harness.dispatch(command, player, "help");

        assertTrue(player.anyMessageContains("mine"), "a player sees the lines: " + player.getMessages());
    }

    @Test
    void theUnknownSubcommandListNamesWhatTheSenderCanReach() {
        FinalCMDPluginCommand command = newHarness().register(new UnknownSubCmdListCmd());
        TestFPlayerSender player = Senders.player("Steve");

        harness.dispatch(command, player, "notASubcommand");

        assertTrue(player.anyMessageContains("mine"), "a player is told about the playerOnly subcommand: " + player.getMessages());
    }
}
