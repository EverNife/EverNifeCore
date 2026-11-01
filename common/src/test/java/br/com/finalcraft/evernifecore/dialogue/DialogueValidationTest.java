package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.testing.DialogueTrees;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A dialogue that cannot run is refused when it is built, with a message that names the node and the way out. */
class DialogueValidationTest {

    @TempDirNobodyCleans
    Path tempDir;

    private DialogueFixture world;

    @BeforeEach
    void open() {
        world = DialogueFixture.open(tempDir);
    }

    @AfterEach
    void close() {
        world.close();
    }

    @Test
    void aGotoToANodeThatDoesNotExistIsRefusedNamingTheOptionAndTheNodes() {
        Dialogue.Builder tomas = Dialogue.builder(world.owner(), "tomas")
                .node(DialogueNode.builder("start").text("Did you bring the logs?")
                        .option(DialogueOption.builder("deliver").text("Here.").goTo("delivery"))
                        .option(DialogueOption.builder("later").text("Not yet.").goTo("later")))
                .node(DialogueNode.builder("later").text("Come back.").option(DialogueOption.builder("bye").text("Bye.").end()));

        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class, tomas::build);

        assertEquals("The dialogue fakecolonies:tomas has option 'deliver' on node 'start' with goto 'delivery' but no node is called"
                + " 'delivery'. Nodes defined: start, later. Add the node or point the option at one of these.", refused.getMessage());
    }

    @Test
    void anEntryNodeThatDoesNotExistIsRefused() {
        Dialogue.Builder talk = Dialogue.builder(world.owner(), "talk").entry("hello")
                .node(DialogueNode.builder("start").text("Hi."));

        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class, talk::build);

        assertTrue(refused.getMessage().contains("starts on the node 'hello' but no node is called 'hello'. Nodes defined: start"),
                refused.getMessage());
    }

    @Test
    void aLoopWithNoWayOutIsRefusedNamingTheTrappedNodes() {
        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class,
                () -> DialogueTrees.withLoopWithoutExit(world.owner(), "haggler").build());

        assertTrue(refused.getMessage().startsWith("The dialogue fakecolonies:haggler has a loop with no way out: nodes [start, haggle,"
                + " insist] only lead back into themselves."), refused.getMessage());
        assertTrue(refused.getMessage().contains("add a node without options"), refused.getMessage());
    }

    @Test
    void rememberWithoutTheCustomVariableIsRefusedTeachingHowToDefineIt() {
        Dialogue.Builder talk = Dialogue.builder(world.owner(), "talk")
                .node(DialogueNode.builder("start").text("Yes or no?").remember("fakecolonies:answer")
                        .option(DialogueOption.builder("yes").text("Yes").end()));

        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class, talk::build);

        assertTrue(refused.getMessage().contains("remembers the answer of node 'start' in 'fakecolonies:answer', but no plugin defined"
                + " that custom variable"), refused.getMessage());
        assertTrue(refused.getMessage().contains("define(\"<name>\", String.class, \"\")"), refused.getMessage());
    }

    @Test
    void rememberIntoAVariableThatIsNotATextIsRefused() {
        world.colonies.customVariables().define("score", BigDecimal.class, BigDecimal.ZERO);
        Dialogue.Builder talk = Dialogue.builder(world.owner(), "talk")
                .node(DialogueNode.builder("start").text("Yes or no?").remember("fakecolonies:score")
                        .option(DialogueOption.builder("yes").text("Yes").end()));

        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class, talk::build);

        assertTrue(refused.getMessage().contains("which holds a BigDecimal"), refused.getMessage());
    }

    @Test
    void aLoopThatCanReachAnEndIsAccepted() {
        //haggle <-> insist loops, but insist can also walk away: the loop has an exit and must not be refused
        Dialogue haggler = Dialogue.builder(world.owner(), "haggler")
                .node(DialogueNode.builder("start").text("How much?").option(DialogueOption.builder("haggle").text("Less!").goTo("haggle")))
                .node(DialogueNode.builder("haggle").text("No.").option(DialogueOption.builder("insist").text("Please!").goTo("insist")))
                .node(DialogueNode.builder("insist").text("Still no.")
                        .option(DialogueOption.builder("again").text("Come on!").goTo("haggle"))
                        .option(DialogueOption.builder("leave").text("Fine.").goTo("bye")))
                .node(DialogueNode.builder("bye").text("Farewell."))
                .build();

        assertEquals(4, haggler.getNodes().size());
        assertTrue(haggler.getNode("bye").isTerminal());
    }
}
