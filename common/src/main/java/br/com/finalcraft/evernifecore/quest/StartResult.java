package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
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

    /** Which check said no - what a {@link QuestPresenter#refusal} rewrites the text by. */
    public enum Reason {
        /** No quest is registered under the id. */
        UNKNOWN_QUEST,
        /** A group quest asked for a player who is in no group of its kind. */
        NO_GROUP,
        /** The owner's quest states are not loaded (the player is offline, or the engine keeps no player quests). */
        NOT_LOADED,
        /** The quest is already active, done, cooling down - or, for an offer, not available. */
        STATUS,
        /** A quest of its {@code requires} is not completed. */
        REQUIRES,
        /** Its window has not opened yet, or has closed. */
        WINDOW,
        /** Asked for an owner of another kind than the quest's {@code owner}. */
        OWNER_KIND,
        /** The gate's conditions failed, or its costs could not be paid; {@link #getGate()} has the failures. */
        GATE,
        /** A listener cancelled the pre event. */
        CANCELLED
    }

    private final Outcome outcome;
    private final @Nullable Reason reason;
    private final @Nullable String why;
    private final @Nullable GateResult gate;
    private final @Nullable QuestState state;

    private StartResult(Outcome outcome, @Nullable Reason reason, @Nullable String why, @Nullable GateResult gate, @Nullable QuestState state) {
        this.outcome = outcome;
        this.reason = reason;
        this.why = why;
        this.gate = gate;
        this.state = state;
    }

    static StartResult done(Outcome outcome, QuestState state) {
        return new StartResult(outcome, null, null, null, state);
    }

    static StartResult denied(GateResult gate) {
        return new StartResult(Outcome.DENIED, Reason.GATE, gate.explain().toPlainText(), gate, null);
    }

    static StartResult refused(Reason reason, String why) {
        return new StartResult(Outcome.REFUSED, reason, why, null, null);
    }

    /** The refusal of group quest {@code definition} asked for {@code requested}, who is in no group of its kind. */
    public static StartResult noGroup(QuestDefinition definition, ProgressOwner requested) {
        return refused(Reason.NO_GROUP, "Quest '" + definition.getId() + "' is a group quest" + (definition.getOwnerProvider() == null ? ""
                : " of " + definition.getOwnerProvider()) + ", and " + requested + " is in no such group: join one first.");
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isSuccess() {
        return outcome == Outcome.STARTED || outcome == Outcome.OFFERED;
    }

    /** Which check said no; null on success. */
    public @Nullable Reason getReason() {
        return reason;
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
        return outcome + (reason == null ? "" : " (" + reason + ")") + (why == null ? "" : ": " + why);
    }
}
