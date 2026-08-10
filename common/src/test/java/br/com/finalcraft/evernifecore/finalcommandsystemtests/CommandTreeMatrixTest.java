package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.CommandShapeErrors;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.ReferenceCommandTree;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tree matrix, run over the engine's own {@link ReferenceCommandTree} - four levels, a capture on
 * two of them, a two-token capture, a flag declared on a node and a variadic tail.
 * <p>
 * It is deliberately written the way a downstream plugin would write it: nothing here knows a fixture
 * class, only {@link Commands#referenceTree()} and the assertions the harness publishes. If this test
 * needs something the engine does not have, the engine is what is missing it.
 */
class CommandTreeMatrixTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private ReferenceCommandTree tree;
    private FinalCMDPluginCommand command;
    private TestCommandSender admin;

    @BeforeEach
    void setup() {
        harness = Commands.harness("TreeMatrix", tempDir);
        tree = Commands.referenceTree();
        command = harness.register(tree);
        admin = new TestCommandSender("admin")
                .grant(ReferenceCommandTree.PERMISSION)
                .grant(ReferenceCommandTree.USER_PERMISSION);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    // ------------------------------------------------------------------
    // Shape
    // ------------------------------------------------------------------

    @Test
    void theTreeIsFourLevelsDeepWithACaptureOnTwoOfThem() {
        harness.tree(command)
                .at("").hasChildren("ping", "user")
                .at("ping").isExecutable().eats()
                .at("user").isExecutable().eats("<user>").hasChildren("info", "server")
                .at("user.server").isBranch().eats("<server>", "<world>").hasChildren("say", "show");
    }

    @Test
    void aNodeIsReachableByEveryAliasItDeclares() {
        assertEquals(harness.tree(command).at("user").node(), harness.tree(command).at("u").node());
    }

    @Test
    void aFlagDeclaredOnANodeIsRecognizedByTheWholeSubtree() {
        harness.tree(command)
                .at("user").recognizesFlags("--dry")
                .at("user.server.say").recognizesFlags("--dry");
    }

    @Test
    void aLeafBelowTwoCapturesIsTypedAsTheWholePath() {
        assertEquals(List.of("user", "<user>", "server", "<server>", "<world>", "say"),
                harness.tree(command).at("user.server.say").typedPrefix());
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    @Test
    void theDeepestLeafReceivesBothCapturesAndItsVariadicTail() {
        harness.dispatch(command, admin, "user Steve server survival world say hello there");

        assertEquals(List.of("user.capture(Steve, dry=false)", "user.server.say(Steve, survival/world, hello there)"),
                tree.calls());
    }

    @Test
    void aLeafCanReadOneTokenOfAMultiTokenCapture() {
        harness.dispatch(command, admin, "user Steve server survival world show");

        assertEquals("user.server.show(Steve, survival/world, world)", tree.lastCall());
    }

    @Test
    void aNodeWithAnExecutableRunsInsteadOfPrintingItsHelp() {
        harness.dispatch(command, admin, "user Steve");

        assertEquals("user(Steve)", tree.lastCall());
    }

    @Test
    void aFlagDeclaredOnANodeIsReadAnywhereBeforeTheTailOfTheDeepestLine() {
        harness.dispatch(command, admin, "user Steve server survival world say --dry hello there");

        //the flag belongs to the node four levels up and is still read at the far end of the line,
        //where the leaf's own window begins - the capture gets it, the leaf never sees the token
        assertEquals(List.of("user.capture(Steve, dry=true)", "user.server.say(Steve, survival/world, hello there)"),
                tree.calls());
    }

    @Test
    void thatSameFlagWrittenInsideTheTailIsPartOfTheMessage() {
        harness.dispatch(command, admin, "user Steve server survival world say hello there --dry");

        //a variadic tail is a stretch of line somebody reads as typed: editing a marker out of it would
        //change the sentence, so the scan stops where the tail opens
        assertEquals(List.of("user.capture(Steve, dry=false)", "user.server.say(Steve, survival/world, hello there --dry)"),
                tree.calls());
    }

    @Test
    void aFlagWrittenBeforeTheEndOfThePathIsRefused() {
        harness.dispatch(command, admin, "--dry user Steve");

        assertTrue(tree.calls().isEmpty(), "nothing may run when the flag comes before the path");
        //the message has to say where the flag DOES go, not just that this line failed
        admin.assertAnyMessageContains("--dry");
        admin.assertAnyMessageContains("comes after the subcommand that declares it");
    }

    // ------------------------------------------------------------------
    // Tab, and what a sender is NOT shown
    // ------------------------------------------------------------------

    @Test
    void tabWalksTheTreeToTheDeepestNode() {
        harness.tree(command)
                .at("").offersTo(admin, "ping", "user")
                .at("user").offersTo(admin, "info", "server")
                .at("user.server").offersTo(admin, "say", "show");
    }

    @Test
    void aWholeSubtreeDisappearsForASenderWithoutItsPermission() {
        TestCommandSender guest = new TestCommandSender("guest").grant(ReferenceCommandTree.PERMISSION);

        harness.tree(command).at("").offersTo(guest, "ping");
        assertEquals(List.of(), harness.tab(command, guest, "user", "Steve", ""));
    }

    // ------------------------------------------------------------------
    // The published shape-error battery, as a whole
    // ------------------------------------------------------------------

    @Test
    void theFrameworkRefusesEveryShapeTheEngineSaysItRefuses() {
        List<String> failures = CommandShapeErrors.check(harness);

        assertTrue(failures.isEmpty(), "the shape-error battery disagreed with the framework:\n" + String.join("\n", failures));
    }
}
