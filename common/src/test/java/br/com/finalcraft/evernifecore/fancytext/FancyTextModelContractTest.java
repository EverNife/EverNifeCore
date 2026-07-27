package br.com.finalcraft.evernifecore.fancytext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the model contract shared by both {@link FancyText} implementations: a leaf
 * ({@link FancySegment}) and a chain of leaves ({@link FancyFormatter}) render, compare and count
 * their children consistently.
 */
public class FancyTextModelContractTest {

    @Test
    void bothImplementationsAreFancyTextAndTheEmptyFactoryStartsWithNoSegment() {
        assertTrue(FancyText.of() instanceof FancySegment, "FancyText.of() must produce a segment");
        assertTrue(FancyFormatter.of() instanceof FancyText, "a FancyFormatter is a FancyText");
        assertEquals(0, FancyFormatter.of().getFancyTextList().size(),
                "FancyFormatter.of() must start with no segments, exactly like new FancyFormatter()");
    }

    @Test
    void formatterFactoryAndConstructorAgreeOnChildCount() {
        assertEquals(new FancyFormatter().getFancyTextList().size(),
                FancyFormatter.of().getFancyTextList().size(),
                "of() and the constructor must start with the same number of segments");
    }

    @Test
    void clickTypeAppliesToTheLastAppendedSegment() {
        FancyFormatter formatter = (FancyFormatter) FancyText.of("head").append("child");
        formatter.setClickType(ClickActionType.OPEN_URL);

        List<FancyText> children = formatter.getFancyTextList();
        assertEquals(ClickActionType.OPEN_URL,
                children.get(children.size() - 1).getClickActionType());
    }

    @Test
    void formattersWithDifferentContentAreNotEqual() {
        assertNotEquals(FancyFormatter.of("&aone"), FancyFormatter.of("&btwo"));
    }
}
