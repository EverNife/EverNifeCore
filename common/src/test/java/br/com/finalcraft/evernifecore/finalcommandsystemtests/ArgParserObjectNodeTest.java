package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserObjectNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two grammars of {@link ArgParserObjectNode#parse(String)}: a JSON object, or {@code key=value} pairs. */
class ArgParserObjectNodeTest {

    @Test
    void aJsonObjectKeepsItsTypes() {
        ObjectNode object = ArgParserObjectNode.parse("  {\"revert\":\"spawn\",\"stuckAfter\":5}");

        assertEquals("spawn", object.get("revert").asText());
        assertTrue(object.get("stuckAfter").isInt());
    }

    @Test
    void keyValuePairsAreTextAndQuotesKeepSpaces() {
        ObjectNode object = ArgParserObjectNode.parse("revert=spawn note=\"two words\"");

        assertEquals("spawn", object.get("revert").asText());
        assertEquals("two words", object.get("note").asText());
        assertTrue(object.get("revert").isTextual());
    }

    @Test
    void jsonThatIsNotAnObjectOrDoesNotParseIsRefusedNamingBothGrammars() {
        IllegalArgumentException array = assertThrows(IllegalArgumentException.class, () -> ArgParserObjectNode.parse("[1]"));
        assertTrue(array.getMessage().contains("key=value"), array.getMessage());

        IllegalArgumentException broken = assertThrows(IllegalArgumentException.class, () -> ArgParserObjectNode.parse("{"));
        assertTrue(broken.getMessage().contains("JSON object") && broken.getMessage().contains("key=value"), broken.getMessage());
    }

    @Test
    void aTokenWithoutEqualsIsRefusedByName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> ArgParserObjectNode.parse("revert=spawn oops"));

        assertTrue(refused.getMessage().contains("'oops'"), refused.getMessage());
    }

    @Test
    void blankTextIsAnEmptyObject() {
        assertEquals(0, ArgParserObjectNode.parse("").size());
        assertEquals(0, ArgParserObjectNode.parse("   ").size());
    }
}
