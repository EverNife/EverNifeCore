package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.text.ITextMetrics;

/**
 * Measures chat text against the vanilla default font.
 *
 * <p>Widths come from the default font's own glyph table (values complete as of 1.19.3), not from
 * {@code org.bukkit.map.MinecraftFont} - that is the map font, a different asset that disagrees on
 * eight ASCII characters, on most of Latin-1, and that has no glyph at all above U+0191.
 *
 * <p>Coverage is Latin-1 (U+0000-U+00FF), which spans every locale this project ships. Anything
 * outside it is charged the width of the missing-glyph box, so a line of Cyrillic or CJK centres
 * approximately rather than exactly.
 *
 * <p>Four font systems have shipped - {@code glyph_sizes.bin} up to 1.12.2, JSON providers from
 * 1.13, full Unicode coverage from 1.16, and unihex from 1.20 - but they changed which glyphs exist
 * and how they are loaded, not how wide the Latin-1 ones are, so one table serves every version.
 *
 * <p>Measurement is always best-effort: chat width, GUI scale, forced unicode font and resource
 * packs are client-side settings a server cannot see, and all of them move the real result.
 */
public class McTextMetrics implements ITextMetrics {

    public static final McTextMetrics INSTANCE = new McTextMetrics();

    /** Chat spans 320 pixels with the width slider at its default maximum. */
    private static final int CHAT_LINE_WIDTH = 320;

    /** Every drawn glyph is followed by one pixel of spacing, and bold adds one more. */
    private static final int GLYPH_SPACING = 1;

    /** Charged for any code point the table below does not cover: the width of the missing-glyph box. */
    private static final int MISSING_GLYPH_WIDTH = 4;

    private static final char COLOR_CHAR = '§';

    /** Glyph widths by code point, {@code -1} where the font has no glyph. */
    private static final byte[] GLYPH_WIDTH = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,   // U+00_
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,   // U+01_
             3,  1,  3,  5,  5,  5,  5,  1,  3,  3,  3,  5,  1,  5,  1,  5,   // U+02_
             5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  1,  1,  4,  5,  4,  5,   // U+03_
             6,  5,  5,  5,  5,  5,  5,  5,  5,  3,  5,  5,  5,  5,  5,  5,   // U+04_
             5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  3,  5,  3,  5,  5,   // U+05_
             2,  5,  5,  5,  5,  5,  4,  5,  5,  1,  5,  4,  2,  5,  5,  5,   // U+06_
             5,  5,  5,  5,  3,  5,  5,  5,  5,  5,  5,  3,  1,  3,  6, -1,   // U+07_
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,   // U+08_
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,   // U+09_
            -1,  1,  5,  5,  7,  5,  1,  0,  3,  7,  4,  6,  5,  3,  7,  5,   // U+0A_
             4,  5,  4,  4,  2,  5,  6,  1, -1,  3,  4,  6,  7,  7,  7,  5,   // U+0B_
             5,  5,  5,  5,  5,  5,  9,  5,  5,  5,  5,  5,  3,  3,  3,  3,   // U+0C_
             6,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,   // U+0D_
             5,  5,  5,  5,  5,  5,  9,  5,  5,  5,  5,  5,  2,  2,  3,  3,   // U+0E_
             5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5,   // U+0F_
    };

    @Override
    public int widthOf(String text) {
        if (text == null || text.isEmpty()) return 0;

        int width = 0;
        boolean bold = false;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);

            if (codePoint == COLOR_CHAR && i + 1 < text.length()) {
                bold = boldAfter(text.charAt(i + 1), bold);
                i += 2;   //the code and its argument render nothing
                continue;
            }

            int glyphWidth = glyphWidthOf(codePoint);
            width += glyphWidth + GLYPH_SPACING;
            if (bold && glyphWidth > 0) width++;

            i += Character.charCount(codePoint);
        }

        return width;
    }

    @Override
    public int chatLineWidth() {
        return CHAT_LINE_WIDTH;
    }

    @Override
    public String resetSequence() {
        return "§r";
    }

    private static int glyphWidthOf(int codePoint) {
        if (codePoint < 0 || codePoint >= GLYPH_WIDTH.length) return MISSING_GLYPH_WIDTH;
        byte width = GLYPH_WIDTH[codePoint];
        return width < 0 ? MISSING_GLYPH_WIDTH : width;
    }

    /**
     * Bold state after a formatting code. Colour codes - including the six hex digits that follow
     * {@code §x} - clear every active format, which is why bold has to be tracked and not merely
     * switched on.
     */
    private static boolean boldAfter(char code, boolean bold) {
        char lower = Character.toLowerCase(code);
        if (lower == 'l') return true;
        if (lower == 'r') return false;
        if ((lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')) return false;
        return bold;
    }

}
