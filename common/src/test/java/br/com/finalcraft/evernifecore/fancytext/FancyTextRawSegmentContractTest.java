package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FancyText#raw(String)}: text a player typed renders as written inside a decorated line, while
 * the normal segment beside it keeps translating {@code &} codes.
 */
@ECoreTest
public class FancyTextRawSegmentContractTest {

    @Test
    void aRawSegmentKeepsTheAmpersandLiteralWhileTheNormalOneColours() {
        assertEquals("&cHello", FancyText.raw("&cHello").toPlainText(), "the raw segment must keep '&c' as text");
        assertEquals("Hello", FancyText.of("&cHello").toPlainText(), "the normal segment must still read '&c' as red");
    }

    @Test
    void aRawSegmentDoesNotTurnRedEvenWithHoverAndClick() {
        FancyText line = FancyText.raw("&cHello").setHover("&7typed by Steve").setClickSuggest("/msg Steve ");
        assertNull(line.toComponent().color(), "a raw '&c' must not colour the text");
        assertEquals("&cHello", line.toPlainText());
    }

    @Test
    void aRawSegmentDoesNotResolvePlaceholders() {
        FancyText line = FancyText.raw("${secret}").addPlaceholder("secret", "leaked");
        assertEquals("${secret}", line.toPlainText(), "a player's '${key}' must not read a placeholder");
    }

    @Test
    void aRawSegmentInheritsTheColourActiveWhereItStarts() {
        FancyFormatter chain = FancyText.of("&a<Steve> ").append(FancyText.raw("&cHello"));
        String legacy = chain.toLegacyString();
        assertTrue(legacy.endsWith("&cHello"), "the raw text must come out as typed: " + legacy);
        assertEquals("<Steve> &cHello", chain.toPlainText());
        assertEquals(NamedTextColor.GREEN, FancyText.raw("x").render("§a", RenderContext.empty()).getComponent().color(),
                "the colour carried in from the previous piece must still apply to the raw text");
    }

    @Test
    void aCopyStaysRaw() {
        assertEquals("&cHello", FancyText.raw("&cHello").copy().toPlainText());
    }
}
