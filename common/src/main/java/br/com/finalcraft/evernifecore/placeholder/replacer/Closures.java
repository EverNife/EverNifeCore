package br.com.finalcraft.evernifecore.placeholder.replacer;

import java.util.regex.Pattern;

/**
 * The delimiter pairs a placeholder can be written with. {@link #DOLLAR_CURLY} is EverNifeCore's own
 * canonical form; {@link #PERCENT} is reserved for PlaceholderAPI pass-through and {@link #BRACKET}
 * for manipulator templates.
 */
public enum Closures {
    DOLLAR_CURLY("${", "}"),
    BRACKET("{", "}"),
    PERCENT("%", "%");

    private final String head;
    private final String tail;
    private final Pattern pattern;

    Closures(final String head, final String tail) {
        this.head = head;
        this.tail = tail;
        // The key is any run of characters that cannot be part of this closure's own delimiters, so
        // one placeholder can never swallow the opening delimiter of the next.
        this.pattern = Pattern.compile(
                escaped(head)
                        + "(?<key>[^" + escaped(distinctChars(head + tail)) + "]+)"
                        + escaped(tail)
        );
    }

    private static String escaped(String literal) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            escaped.append('\\').append(literal.charAt(i));
        }
        return escaped.toString();
    }

    private static String distinctChars(String text) {
        StringBuilder distinct = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (distinct.indexOf(String.valueOf(text.charAt(i))) < 0) {
                distinct.append(text.charAt(i));
            }
        }
        return distinct.toString();
    }

    public String quote(String text) {
        return head + text + tail;
    }

    public String getHead() {
        return head;
    }

    public String getTail() {
        return tail;
    }

    public Pattern getPattern() {
        return pattern;
    }

    /** The closure that owns {@code pattern}, or {@code null} when it came from somewhere else. */
    public static Closures ofPattern(Pattern pattern) {
        for (Closures closures : values()) {
            if (closures.pattern == pattern) {
                return closures;
            }
        }
        return null;
    }
}
