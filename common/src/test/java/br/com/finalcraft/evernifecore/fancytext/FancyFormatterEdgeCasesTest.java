package br.com.finalcraft.evernifecore.fancytext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FancyFormatterEdgeCasesTest {

    // copy() must deep-copy the placeholder map: mutating one side never leaks into the other.
    @Test
    public void copyIsolatesThePlaceholderMap() {
        FancyFormatter original = new FancyFormatter().append("hello");
        original.addPlaceholder("%a%", "1");

        FancyFormatter copy = original.copy();
        copy.addPlaceholder("%x%", "v");
        assertFalse(original.mapOfPlaceholders.containsKey("%x%"), "copy leaked into original");
        assertTrue(copy.mapOfPlaceholders.containsKey("%x%"));

        original.addPlaceholder("%y%", "2");
        assertFalse(copy.mapOfPlaceholders.containsKey("%y%"), "original leaked into copy");
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
