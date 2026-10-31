package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.GateResult;
import jakarta.annotation.Nullable;

/** What {@link QuestEngine#start} or {@link QuestEngine#offer} did, and why when it did nothing. */
public final class StartResult {

    public enum Outcome {
        STARTED,
        OFFERED,
        /** The gate denied it; {@link #getGate()} has every failure. */
        DENIED,
        /** Something else said no: unknown quest, not loaded, already active, a cancelled event... {@link #getWhy()} says what. */
        REFUSED
    }

    private final Outcome outcome;
    private final @Nullable String why;
    private final @Nullable GateResult gate;
    private final @Nullable QuestState state;

    private StartResult(Outcome outcome, @Nullable String why, @Nullable GateResult gate, @Nullable QuestState state) {
        this.outcome = outcome;
        this.why = why;
        this.gate = gate;
        this.state = state;
    }

    static StartResult done(Outcome outcome, QuestState state) {
        return new StartResult(outcome, null, null, state);
    }

    static StartResult denied(GateResult gate) {
        return new StartResult(Outcome.DENIED, gate.explain().toPlainText(), gate, null);
    }

    static StartResult refused(String why) {
        return new StartResult(Outcome.REFUSED, why, null, null);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isSuccess() {
        return outcome == Outcome.STARTED || outcome == Outcome.OFFERED;
    }

    /** Why nothing happened; null on success. */
    public @Nullable String getWhy() {
        return why;
    }

    /** The gate's answer when it denied the start. */
    public @Nullable GateResult getGate() {
        return gate;
    }

    /** A snapshot of the state after a success. */
    public @Nullable QuestState getState() {
        return state;
    }

    @Override
    public String toString() {
        return outcome + (why == null ? "" : ": " + why);
    }
}
