package br.com.finalcraft.evernifecore.fancytext.hover;

import java.util.Objects;

/** The plain-tooltip hover: renders as {@code HoverEvent.showText(...)} of a (possibly multi-line) string. */
public final class TextHover implements FancyHover {

    public static final String TYPE_ID = "text";

    private final String text;

    public TextHover(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    @Override
    public String toLegacyPayload() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextHover)) return false;
        return Objects.equals(text, ((TextHover) o).text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @Override
    public String toString() {
        return "TextHover{text='" + text + "'}";
    }
}
