package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What {@link Gate#check} or {@link Gate#pay} answered. Immutable. */
public final class GateResult {

    public enum Outcome {
        /** The check passed; nothing was taken. */
        PASSED,
        /** The check failed, a listener cancelled the payment, or the first cost did not come out; nothing stays taken. */
        DENIED,
        /** Every cost was taken. */
        PAID,
        /** A cost did not come out after others were taken; those were given back. */
        REFUNDED
    }

    private final Gate gate;
    private final ExecutionContext context;
    private final Outcome outcome;
    private final List<ConditionResult> failures;
    private final List<ConditionResult> notes;

    GateResult(Gate gate, ExecutionContext context, Outcome outcome, List<ConditionResult> failures, List<ConditionResult> notes) {
        this.gate = gate;
        this.context = context;
        this.outcome = outcome;
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    GateResult as(Outcome newOutcome, List<ConditionResult> newFailures) {
        return new GateResult(gate, context, newOutcome, newFailures, notes);
    }

    public Gate getGate() {
        return gate;
    }

    public ExecutionContext getContext() {
        return context;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    /** PASSED or PAID. */
    public boolean isPassed() {
        return outcome == Outcome.PASSED || outcome == Outcome.PAID;
    }

    /** Every failure, in declaration order - conditions first, then costs. */
    public List<ConditionResult> getFailures() {
        return failures;
    }

    /** Failures reported without denying ({@code optional}). */
    public List<ConditionResult> getNotes() {
        return notes;
    }

    /** Every cost described, joined by {@code ", "}: "100 coins, 1 use of vip-door". */
    public String describeCosts() {
        List<String> described = new ArrayList<>();
        for (Cost cost : gate.getCosts()) {
            described.add(cost.describe(context).toLegacyString());
        }
        return String.join(", ", described);
    }

    /** The failures, one per line; empty when it passed. */
    public FancyText explain() {
        return failures.isEmpty() ? FancyText.of() : FancyText.join("\n", failures, ConditionResult::getWhy);
    }

    @Override
    public String toString() {
        return outcome + (failures.isEmpty() ? "" : " " + failures);
    }
}
