package br.com.finalcraft.evernifecore.fancytext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FancyFormatterEdgeCasesTest {

    // clone() must deep-copy the placeholder map: mutating one side never leaks into the other.
    @Test
    public void cloneIsolatesThePlaceholderMap() {
        FancyFormatter original = new FancyFormatter().append("hello");
        original.addPlaceholder("%a%", "1");

        FancyFormatter clone = original.clone();
        clone.addPlaceholder("%x%", "v");
        assertFalse(original.mapOfPlaceholders.containsKey("%x%"), "clone leaked into original");
        assertTrue(clone.mapOfPlaceholders.containsKey("%x%"));

        original.addPlaceholder("%y%", "2");
        assertFalse(clone.mapOfPlaceholders.containsKey("%y%"), "original leaked into clone");
    }

    // An empty formatter has no segment to delegate to; getters/setters degrade instead of throwing.
    @Test
    public void emptyFormatterGettersAndSettersDoNotThrow() {
        FancyFormatter empty = new FancyFormatter();

        assertNull(empty.getText());
        assertNull(empty.getHoverText());
        assertNull(empty.getClickActionText());
        assertEquals(ClickActionType.NONE, empty.getClickActionType());

        assertDoesNotThrow(() -> empty.hover("hover"));
        assertDoesNotThrow(() -> empty.click("cmd", ClickActionType.RUN_COMMAND));
        assertDoesNotThrow(() -> empty.clickCommand("cmd"));
        assertDoesNotThrow(() -> empty.clickSuggest("suggest"));
        assertDoesNotThrow(() -> empty.clickLink("https://example.com"));
    }
}
