package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.GateResult;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What {@link QuestPool#reroll} did: the new draw, or why it did not reroll (the gate's failures, the cooldown, an owner not loaded). */
public final class RerollResult {

    private final List<QuestId> drawn;
    private final @Nullable GateResult gate;
    private final @Nullable String refusal;

    private RerollResult(List<QuestId> drawn, @Nullable GateResult gate, @Nullable String refusal) {
        this.drawn = Collections.unmodifiableList(new ArrayList<>(drawn));
        this.gate = gate;
        this.refusal = refusal;
    }

    static RerollResult done(List<QuestId> drawn) {
        return new RerollResult(drawn, null, null);
    }

    static RerollResult denied(GateResult gate) {
        return new RerollResult(Collections.<QuestId>emptyList(), gate, null);
    }

    static RerollResult refused(String why) {
        return new RerollResult(Collections.<QuestId>emptyList(), null, why);
    }

    public boolean isSuccess() {
        return gate == null && refusal == null;
    }

    /** The owner's draw after the reroll; empty when it did not reroll. */
    public List<QuestId> getDrawn() {
        return drawn;
    }

    /** The gate that refused it; null otherwise. */
    public @Nullable GateResult getGate() {
        return gate;
    }

    /** Why it did not reroll, in words: the gate's failures or the refusal; empty on success. */
    public String explain() {
        if (gate != null) {
            return gate.explain().toPlainText();
        }
        return refusal == null ? "" : refusal;
    }

    @Override
    public String toString() {
        return isSuccess() ? "rerolled " + drawn : "not rerolled: " + explain();
    }
}
