package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.util.Objects;

/** One line of the quest tracker: a text and, when {@link #getNeeded()} is above zero, a {@code current/needed} count. */
public final class TrackerLine {

    private final FancyText text;
    private final int current;
    private final int needed;

    private TrackerLine(FancyText text, int current, int needed) {
        this.text = Objects.requireNonNull(text, "text");
        this.current = current;
        this.needed = needed;
    }

    /** A line with a count; {@code needed <= 0} is a line without one. */
    public static TrackerLine of(FancyText text, int current, int needed) {
        return new TrackerLine(text, current, needed);
    }

    /** A line without a count. */
    public static TrackerLine of(FancyText text) {
        return new TrackerLine(text, 0, 0);
    }

    public FancyText getText() {
        return text;
    }

    public int getCurrent() {
        return current;
    }

    public int getNeeded() {
        return needed;
    }

    public boolean hasCount() {
        return needed > 0;
    }

    @Override
    public String toString() {
        return text.getText() + (hasCount() ? " " + current + "/" + needed : "");
    }
}
