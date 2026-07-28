package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.text.ITextMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Geometry only: a monospaced stand-in stands for any platform's font, so what is under test is the
// arithmetic and never a particular glyph table.
public class FCTextUtilTest {

    /** Every character is one unit wide, on a line of {@code lineWidth} units. */
    private static ITextMetrics monospaced(int lineWidth) {
        return new ITextMetrics() {
            @Override
            public int widthOf(String text) {
                return text == null ? 0 : text.length();
            }

            @Override
            public int chatLineWidth() {
                return lineWidth;
            }

            @Override
            public String resetSequence() {
                return "|";
            }
        };
    }

    @Test
    public void centringIndentsByHalfTheLeftoverWidth() {
        assertEquals("|   abcd|", FCTextUtil.alignCenter(monospaced(10), "abcd"));
    }

    @Test
    public void centringWithABorderFillsBothSides() {
        assertEquals("|---|abcd|---|", FCTextUtil.alignCenter(monospaced(10), "abcd", "-"));
    }

    @Test
    public void aStraightLineStopsShortOfOverflowing() {
        assertEquals("|---|", FCTextUtil.straightLineOf(monospaced(3), "-"));
        assertEquals("|ababab|", FCTextUtil.straightLineOf(monospaced(7), "ab"));
    }

    @Test
    public void textWiderThanTheLineGetsNoPadding() {
        assertEquals("|abcdefghijkl|", FCTextUtil.alignCenter(monospaced(4), "abcdefghijkl"));
    }

    @Test
    public void canExceedRoundsToTheNearestFitInsteadOfStoppingShort() {
        assertEquals("aaaa", FCTextUtil.generateWidth(monospaced(10), "a", 4, false));
        assertEquals("abab", FCTextUtil.generateWidth(monospaced(10), "ab", 5, false));
        assertEquals("ababab", FCTextUtil.generateWidth(monospaced(10), "ab", 5, true));
    }

    @Test
    public void aZeroWidthFillWouldRepeatForeverAndIsRefused() {
        assertThrows(IllegalStateException.class, () -> FCTextUtil.generateWidth(monospaced(10), "", 4, false));
    }

    // A platform that cannot measure its chat surface must hand the text back untouched rather than
    // lay it out against a width it invented.
    @Test
    public void unmeasuredTextIsReturnedUntouched() {
        String text = "abcd";

        assertSame(text, FCTextUtil.alignCenter(ITextMetrics.UNMEASURED, text));
        assertSame(text, FCTextUtil.alignCenter(ITextMetrics.UNMEASURED, text, "-"));
        assertSame(text, FCTextUtil.straightLineOf(ITextMetrics.UNMEASURED, text));
    }

    @Test
    public void repeatingIsANoOpForNonPositiveCounts() {
        assertEquals("", FCTextUtil.repeatString("ab", 0));
        assertEquals("", FCTextUtil.repeatString("ab", -3));
        assertEquals("ababab", FCTextUtil.repeatString("ab", 3));
    }

}
