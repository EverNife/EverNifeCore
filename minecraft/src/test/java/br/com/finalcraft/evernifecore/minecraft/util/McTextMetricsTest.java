package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pure table lookups and string arithmetic: no Bukkit type is touched, so this runs headless.
public class McTextMetricsTest {

    private final ITextMetrics metrics = McTextMetrics.INSTANCE;

    @Test
    public void chargesGlyphWidthPlusOnePixelOfSpacing() {
        assertEquals(6, metrics.widthOf("A"));
        assertEquals(6, metrics.widthOf("a"));
        assertEquals(2, metrics.widthOf("i"));
        assertEquals(4, metrics.widthOf(" "));
        assertEquals(7, metrics.widthOf("@"));
    }

    @Test
    public void sumsTheAdvancesOfEveryCharacter() {
        assertEquals(0, metrics.widthOf(""));
        assertEquals(0, metrics.widthOf(null));
        assertEquals(6 + 2 + 6, metrics.widthOf("Aia"));
    }

    // The map font Bukkit exposes charges 8 for these; the chat font charges 6. Locales with
    // accents centre correctly only if the chat font's numbers are the ones used.
    @Test
    public void measuresAccentedLatinAgainstTheChatFontNotTheMapFont() {
        assertEquals(6, metrics.widthOf("é"));
        assertEquals(6, metrics.widthOf("ç"));
        assertEquals(6, metrics.widthOf("ã"));
        assertEquals(6, metrics.widthOf("Ç"));
    }

    @Test
    public void formattingCodesRenderNothing() {
        assertEquals(0, metrics.widthOf("§a"));
        assertEquals(0, metrics.widthOf("§a§l§r"));
        assertEquals(6, metrics.widthOf("§aA"));
    }

    @Test
    public void boldWidensEveryGlyphThatFollowsIt() {
        assertEquals(7, metrics.widthOf("§lA"));
        assertEquals(5, metrics.widthOf("§l "));
        assertEquals(7 + 6, metrics.widthOf("§lA§rA"));
    }

    // A colour applies on its own and clears whatever formatting was active, so bold cannot be
    // tracked as a latch that only §r opens.
    @Test
    public void colourCodesClearBold() {
        assertEquals(7 + 6, metrics.widthOf("§lA§aA"));
        assertEquals(6, metrics.widthOf("§l§aA"));
    }

    // §x introduces a hex colour as six further code pairs; all of it renders nothing and, being a
    // colour, leaves bold off.
    @Test
    public void hexColourSequencesRenderNothingAndClearBold() {
        assertEquals(0, metrics.widthOf("§x§1§2§3§4§5§6"));
        assertEquals(6, metrics.widthOf("§x§a§b§c§d§e§fA"));
        assertEquals(6, metrics.widthOf("§l§x§1§2§3§4§5§6A"));
    }

    // Documented limit of the Latin-1 table: beyond it every code point is charged the
    // missing-glyph box, so a Cyrillic line centres approximately.
    @Test
    public void chargesTheMissingGlyphBoxBeyondLatin1() {
        assertEquals(5, metrics.widthOf("ю"));
        assertEquals(5, metrics.widthOf("★"));
    }

    @Test
    public void trailingLoneColourCharIsAGlyph() {
        assertEquals(1, metrics.widthOf("§"));
    }

    @Test
    public void chatSpansThreeHundredAndTwentyPixels() {
        assertEquals(320, metrics.chatLineWidth());
    }

    @Test
    public void straightLineFillsTheLineWithoutOverflowing() {
        String line = FCTextUtil.straightLineOf(metrics, "-");

        assertEquals(53, line.replace("§r", "").length());
        assertTrue(metrics.widthOf(line) <= metrics.chatLineWidth());
    }

    // The rule a page prints above its entries is written out as a literal rather than measured, so
    // the number of dashes in it has to be pinned against these metrics somewhere - here.
    @Test
    public void fiftyThreeDashesFillTheChatLineAndFiftyFourOverflowIt() {
        assertTrue(metrics.widthOf(FCTextUtil.repeatString("-", 53)) <= metrics.chatLineWidth());
        assertTrue(metrics.widthOf(FCTextUtil.repeatString("-", 54)) > metrics.chatLineWidth());
    }

    @Test
    public void centringPadsWithHalfTheLeftoverWidth() {
        String centred = FCTextUtil.alignCenter(metrics, "AAAA");

        // 320 - 24 leaves 148 pixels a side, and a space advances 4.
        assertEquals(37, centred.replace("§r", "").indexOf("AAAA"));
    }

    @Test
    public void aStringOfOnlyFormattingCodesCannotFillAnything() {
        assertThrows(IllegalStateException.class, () -> FCTextUtil.straightLineOf(metrics, "§a§l"));
    }

}
