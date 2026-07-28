package br.com.finalcraft.evernifecore.text;

/**
 * Everything the layout helpers in {@link br.com.finalcraft.evernifecore.util.FCTextUtil} need to
 * know about how a platform renders chat text. They do geometry only - fonts, formatting codes and
 * the size of the chat surface all come from here.
 *
 * <p>Widths are advances (how far the cursor moves), not glyph bounding boxes, so the width of a
 * string is the sum of the advances of its characters.
 */
public interface ITextMetrics {

    /**
     * Width of {@code text} as it would render, with the platform's formatting codes contributing
     * nothing themselves but still applying (bold widens the glyphs that follow it).
     */
    int widthOf(String text);

    /**
     * Usable width of a single chat line, in the same unit as {@link #widthOf(String)}. Zero means
     * the platform cannot measure its chat surface; every layout helper then returns its input
     * untouched instead of guessing.
     */
    int chatLineWidth();

    /**
     * The platform's "drop all formatting" sequence, emitted around generated padding so a filled
     * line cannot inherit or leak style. Empty when the platform has no such sequence.
     */
    String resetSequence();

    /**
     * Stands in for a platform that cannot measure text at all: every width is zero, which makes
     * every layout helper a no-op.
     */
    ITextMetrics UNMEASURED = new ITextMetrics() {
        @Override
        public int widthOf(String text) {
            return 0;
        }

        @Override
        public int chatLineWidth() {
            return 0;
        }

        @Override
        public String resetSequence() {
            return "";
        }
    };

}
