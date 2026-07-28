package br.com.finalcraft.evernifecore.hytale.util;

import br.com.finalcraft.evernifecore.text.ITextMetrics;

/**
 * Measures chat text on Hytale.
 *
 * <p>Every number here is provisional. The server API carries no font, glyph or text-measurement
 * type of any kind, and {@code MessageUtil} exposes only colour and ANSI conversion, so there is
 * nothing to read real metrics from yet. Until there is, this models chat as a monospaced grid: one
 * cell per code point, {@link #CHAT_LINE_CELLS} cells to a line. Layout comes out plausible rather
 * than exact.
 *
 * <p>The two constants below are the whole correction surface - replacing them with measured values
 * fixes centring and filled rules everywhere, with no other change.
 */
public class HyTextMetrics implements ITextMetrics {

    public static final HyTextMetrics INSTANCE = new HyTextMetrics();

    /** Provisional: every code point is assumed to occupy one cell. */
    private static final int CELL_WIDTH = 1;

    /** Provisional: assumed number of cells that fit on one chat line. */
    private static final int CHAT_LINE_CELLS = 60;

    @Override
    public int widthOf(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.codePointCount(0, text.length()) * CELL_WIDTH;
    }

    @Override
    public int chatLineWidth() {
        return CHAT_LINE_CELLS * CELL_WIDTH;
    }

    @Override
    public String resetSequence() {
        // Hytale's Message model carries style as structured fields, not as an inline escape that a
        // plain String could reopen or close.
        return "";
    }

}
