package br.com.finalcraft.evernifecore.fancytext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FancyFormatterEdgeCasesTest {

    // copy() must isolate the declarations: declaring on one side never leaks into the other.
    @Test
    public void copyIsolatesThePlaceholderMap() {
        FancyFormatter original = new FancyFormatter().append("${x} ${y}");
        original.addPlaceholder("a", "1");

        FancyFormatter copy = original.copy();
        copy.addPlaceholder("x", "onlyInTheCopy");
        assertFalse(original.getPlaceholderProvider().getParserMap().containsKey("x"), "copy leaked into original");
        assertTrue(copy.getPlaceholderProvider().getParserMap().containsKey("x"));

        original.addPlaceholder("y", "onlyInTheOriginal");
        assertFalse(copy.getPlaceholderProvider().getParserMap().containsKey("y"), "original leaked into copy");

        assertEquals("${x} onlyInTheOriginal", original.toLegacyString(RenderContext.empty()));
        assertEquals("onlyInTheCopy ${y}", copy.toLegacyString(RenderContext.empty()));
    }

    // An empty formatter has no segment to delegate to; getters/setters degrade instead of throwing.
    @Test
    public void emptyFormatterGettersAndSettersDoNotThrow() {
        FancyFormatter empty = new FancyFormatter();

        assertNull(empty.getText());
        assertNull(empty.getHoverText());
        assertNull(empty.getClickActionText());
        assertEquals(ClickActionType.NONE, empty.getClickActionType());

        assertDoesNotThrow(() -> empty.setHover("hover"));
        assertDoesNotThrow(() -> empty.setClick("cmd", ClickActionType.RUN_COMMAND));
        assertDoesNotThrow(() -> empty.setClickCommand("cmd"));
        assertDoesNotThrow(() -> empty.setClickSuggest("suggest"));
        assertDoesNotThrow(() -> empty.setClickLink("https://example.com"));
    }
}
