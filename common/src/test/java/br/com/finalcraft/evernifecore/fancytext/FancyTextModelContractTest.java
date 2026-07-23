package br.com.finalcraft.evernifecore.fancytext;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proves, against the CURRENT API, the confirmed model-level bugs of the FancyText/FancyFormatter
 * pair. Every method here is expected to stay red until the model rewrite lands.
 */
public class FancyTextModelContractTest {

    // FancyFormatter.of() seeds an empty leaf (getOrCreateFormmater() wraps 'this' into the new
    // formatter), while new FancyFormatter() starts truly empty. The codec uses of() to rebuild a
    // formatter on every read, so each read->write cycle grows the segment count by one.
    @Test
    @Tag("known-bug")
    void formatterFactoryAndConstructorAgreeOnChildCount() {
        assertEquals(new FancyFormatter().getFancyTextList().size(),
                FancyFormatter.of().getFancyTextList().size(),
                "of() and the constructor must start with the same number of segments");
    }

    // FancyFormatter does not override setClickAction(ClickActionType): the single-arg call lands
    // on the formatter's own inherited (and never rendered) field instead of the last appended
    // child, so FCLocaleScanner's per-child clickActionType assignment for @FCLocale.Child is a
    // silent no-op.
    @Test
    @Tag("known-bug")
    void clickTypeAppliesToTheLastAppendedSegment() {
        FancyFormatter formatter = (FancyFormatter) FancyText.of("head").append("child");
        formatter.setClickAction(ClickActionType.OPEN_URL);

        List<FancyText> children = formatter.getFancyTextList();
        assertEquals(ClickActionType.OPEN_URL,
                children.get(children.size() - 1).getClickActionType());
    }

    // Every base field of a FancyFormatter stays at its default (the real content lives in its
    // children), so the inherited FancyText.equals() considers ANY two formatters equal regardless
    // of content. ECPluginData relies on this equals() to decide whether a lang file needs rewriting.
    @Test
    @Tag("known-bug")
    void formattersWithDifferentContentAreNotEqual() {
        assertNotEquals(FancyFormatter.of("&aone"), FancyFormatter.of("&btwo"));
    }
}
