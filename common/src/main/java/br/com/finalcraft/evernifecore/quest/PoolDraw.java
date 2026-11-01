package br.com.finalcraft.evernifecore.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * One owner's draw of one {@link QuestPool}: the window it was drawn in, the quests drawn, the last ones drawn before
 * (what {@code noRepeatLast} keeps out), when the owner last rerolled and how many times in this window. A Jackson POJO the engine mutates under the
 * owner's lock.
 */
public final class PoolDraw {

    private String windowKey;
    private List<String> drawn = new ArrayList<>();
    private List<String> history = new ArrayList<>();
    private long rerolledAt;
    private int rerolls;

    public PoolDraw() {
        //Jackson
    }

    /** The window the draw belongs to; a new key means a new draw. */
    public String getWindowKey() {
        return windowKey;
    }

    public void setWindowKey(String windowKey) {
        this.windowKey = windowKey;
    }

    /** The quests drawn, as {@code owner:name}. */
    public List<String> getDrawn() {
        return drawn;
    }

    public void setDrawn(List<String> drawn) {
        this.drawn = drawn == null ? new ArrayList<String>() : drawn;
    }

    /** The latest quests drawn, oldest first, at most {@code noRepeatLast} of them. */
    public List<String> getHistory() {
        return history;
    }

    public void setHistory(List<String> history) {
        this.history = history == null ? new ArrayList<String>() : history;
    }

    /** Epoch millis of the last reroll; 0 for never. */
    public long getRerolledAt() {
        return rerolledAt;
    }

    public void setRerolledAt(long rerolledAt) {
        this.rerolledAt = rerolledAt;
    }

    /** How many times the owner rerolled in this window; a new draw starts it at 0. */
    public int getRerolls() {
        return rerolls;
    }

    public void setRerolls(int rerolls) {
        this.rerolls = rerolls;
    }

    PoolDraw copy() {
        PoolDraw copy = new PoolDraw();
        copy.windowKey = windowKey;
        copy.drawn = new ArrayList<>(drawn);
        copy.history = new ArrayList<>(history);
        copy.rerolledAt = rerolledAt;
        copy.rerolls = rerolls;
        return copy;
    }

    @Override
    public String toString() {
        return drawn + " @" + windowKey;
    }
}
