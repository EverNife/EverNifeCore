package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.text.ITextMetrics;

/**
 * Chat line layout - centring, padding and filled rules - expressed purely as geometry over an
 * {@link ITextMetrics}. Nothing here knows what a font, a colour code or a chat window is; a
 * platform teaches it all of that by handing over its metrics.
 */
public class FCTextUtil {

    private FCTextUtil() {
    }

    /**
     * Centres {@code stringToAlign} on a chat line by prefixing it with spaces. Only the left half
     * is emitted - trailing padding would not shift anything.
     */
    public static String alignCenter(ITextMetrics metrics, String stringToAlign) {
        int lineWidth = metrics.chatLineWidth();
        if (lineWidth <= 0) return stringToAlign;

        int sideWidth = (lineWidth - metrics.widthOf(stringToAlign)) / 2;
        String reset = metrics.resetSequence();

        return reset + generateWidth(metrics, " ", sideWidth, false) + stringToAlign + reset;
    }

    /**
     * Centres {@code stringToAlign} with {@code borderFill} repeated on both sides, so the line
     * reads as a titled rule rather than as indented text.
     */
    public static String alignCenter(ITextMetrics metrics, String stringToAlign, String borderFill) {
        int lineWidth = metrics.chatLineWidth();
        if (lineWidth <= 0) return stringToAlign;

        int sideWidth = (int) Math.floor((lineWidth - metrics.widthOf(stringToAlign)) / 2D);
        String side = generateWidth(metrics, borderFill, sideWidth, false);
        String reset = metrics.resetSequence();

        return reset + side + reset + stringToAlign + reset + side + reset;
    }

    /** {@code string} repeated until it fills exactly one chat line, without overflowing it. */
    public static String straightLineOf(ITextMetrics metrics, String string) {
        int lineWidth = metrics.chatLineWidth();
        if (lineWidth <= 0) return string;

        return metrics.resetSequence() + generateWidth(metrics, string, lineWidth, false) + metrics.resetSequence();
    }

    /**
     * {@code string} repeated to cover {@code width}. {@code canExceed} rounds to the nearest fit
     * instead of stopping short of it.
     */
    public static String generateWidth(ITextMetrics metrics, String string, int width, boolean canExceed) {
        int stringWidth = metrics.widthOf(string);
        // Zero width (e.g. a string of only colour codes) would divide by zero and yield MAX_VALUE repeats.
        if (stringWidth <= 0) throw new IllegalStateException("String with no visible width cannot be used as argument in generateWidth() str: [" + string + "]");

        int count = (int) (canExceed ? Math.round(width / (double) stringWidth) : Math.floor(width / (double) stringWidth));
        return repeatString(string, count);
    }

    public static String repeatString(String string, int count) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            stringBuilder.append(string);
        }
        return stringBuilder.toString();
    }

}
