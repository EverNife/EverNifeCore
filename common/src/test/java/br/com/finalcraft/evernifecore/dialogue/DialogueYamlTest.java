package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.npc.Npcs;
import br.com.finalcraft.evernifecore.testing.DialogueTrees;
import br.com.finalcraft.evernifecore.testing.FakeNpcProvider;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tree a product writes in YAML - the Tomás example - reads into the model, its languages inline. */
class DialogueYamlTest {

    @TempDirNobodyCleans
    Path tempDir;

    private DialogueFixture world;
    private LogicWorld colonies;

    @BeforeEach
    void open() {
        world = DialogueFixture.open(tempDir);
        colonies = LogicWorld.open("FinalColonies", tempDir.resolve("finalcolonies"));
        colonies.customVariables().define("tomas-answer", String.class, "");
        Npcs.register(colonies.getOwner(), new FakeNpcProvider("citizens"));
    }

    @AfterEach
    void close() {
        Dialogues.closeOwnedBy(colonies.getOwner());
        Npcs.unregisterAll(colonies.getOwner());
        colonies.close();
        world.close();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = DialogueYamlTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name + " is on the test classpath");
            return new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        }
    }

    private ExecutionContext in(String locale) {
        return colonies.context(world.alex).locale(Locale.forLanguageTag(locale)).build();
    }

    @Test
    void parseYamlReadsATextAndNamesTheDialogueWhenItIsNotYaml() throws IOException {
        Dialogue tomas = Dialogue.parseYaml(colonies.getOwner(), "tomas", resource("/dialogue/tomas.yml"));
        assertEquals(Arrays.asList("start", "thanks", "later"), Arrays.asList(tomas.getNodes().keySet().toArray()));

        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class,
                () -> Dialogue.parseYaml(colonies.getOwner(), "broken", "entry: [start"));
        assertTrue(refused.getMessage().contains("'broken' of FinalColonies is not valid YAML"), refused.getMessage());
    }

    @Test
    void theTomasExampleReadsWithItsNodesGateGotoAndBothLanguages() throws IOException {
        Dialogue tomas = DialogueTrees.fromYaml(colonies.getOwner(), "tomas", resource("/dialogue/tomas.yml"));
        Dialogues.of(colonies.getOwner()).register(tomas);

        assertEquals(Arrays.asList("start", "thanks", "later"), Arrays.asList(tomas.getNodes().keySet().toArray()));
        assertEquals(Duration.ofMinutes(2), tomas.getTimeout());
        assertEquals(Collections.singletonList(DialogueTrigger.npc(NpcRef.of("citizens", "12"))), tomas.getTriggers());
        assertEquals(1, tomas.getFailWhen().size());
        DialogueNode start = tomas.getNode("start");
        assertEquals("finalcolonies:tomas-answer", start.getRemember());
        DialogueOption deliver = start.getOption("deliver");
        assertEquals("thanks", deliver.getGoto());
        assertEquals(1, deliver.getGate().getCosts().size());
        assertTrue(deliver.getGate().getCosts().get(0).toString().toLowerCase(Locale.ROOT).contains("item"),
                deliver.getGate().getCosts().get(0).toString());
        assertEquals(2, deliver.getEffects().size());
        assertTrue(tomas.getNode("thanks").isTerminal());
        assertTrue(tomas.getNode("later").getOption("bye").isEnd());

        assertEquals("Olá! A colônia precisa de madeira. Trouxe as 32 toras?", tomas.getText(start, in("pt-BR")));
        assertEquals("Hello! The colony needs wood. Did you bring the 32 logs?", tomas.getText(start, in("en-US")));
        assertEquals("Yes, here they are.", tomas.getText(start, deliver, in("en-US")));
        assertEquals("Tomás", tomas.getSpeaker(in("en-US")));
    }

    @Test
    void anUnknownKeyIsRefusedNamingWhereItIs() {
        DialogueDefinitionException refused = assertThrows(DialogueDefinitionException.class, () -> DialogueTrees.fromYaml(colonies.getOwner(),
                "tomas", "nodes:\n  start:\n    text: Hi\n    options:\n      bye: {text: Bye, ends: true}\n"));

        assertTrue(refused.getMessage().startsWith("The dialogue finalcolonies:tomas node 'start' option 'bye' has the key 'ends'"),
                refused.getMessage());
    }
}
