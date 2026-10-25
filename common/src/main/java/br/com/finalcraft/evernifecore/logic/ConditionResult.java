package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The answer of a {@link Condition}: passed, or failed with why. A composition that fails carries every failure of its
 * children ({@link #getFailures()}); a passed {@code optional} carries its child's failures as {@link #getNotes() notes},
 * reported and never denying. Immutable.
 */
public final class ConditionResult {

    private static final ConditionResult PASS = new ConditionResult(true, null, null, Collections.<ConditionResult>emptyList(),
            Collections.<ConditionResult>emptyList());

    private final boolean passed;
    private final @Nullable FancyText why;
    private final @Nullable Condition condition;
    private final List<ConditionResult> failures; //empty for a leaf: the leaf is its own failure
    private final List<ConditionResult> notes;

    private ConditionResult(boolean passed, @Nullable FancyText why, @Nullable Condition condition, List<ConditionResult> failures,
                            List<ConditionResult> notes) {
        this.passed = passed;
        this.why = why;
        this.condition = condition;
        this.failures = failures;
        this.notes = notes;
    }

    public static ConditionResult pass() {
        return PASS;
    }

    public static ConditionResult fail(FancyText why) {
        return new ConditionResult(false, Objects.requireNonNull(why, "a failure says why"), null, Collections.<ConditionResult>emptyList(),
                Collections.<ConditionResult>emptyList());
    }

    public static ConditionResult of(boolean passed, FancyText whyWhenNot) {
        return passed ? PASS : fail(whyWhenNot);
    }

    /** A composition failed because of {@code failures}, each a failed result of a child. */
    public static ConditionResult failAll(List<ConditionResult> failures) {
        List<ConditionResult> leaves = new ArrayList<>();
        for (ConditionResult failure : failures) {
            leaves.addAll(failure.getFailures());
        }
        if (leaves.isEmpty()) {
            throw new IllegalArgumentException("a composition fails because of at least one failed child");
        }
        FancyText why = leaves.size() == 1 ? leaves.get(0).why : FancyText.join("\n", leaves, ConditionResult::getWhy);
        return new ConditionResult(false, why, null, Collections.unmodifiableList(leaves), Collections.<ConditionResult>emptyList());
    }

    /** This result with {@code notes} added; a note is a failure reported without denying. */
    public ConditionResult withNotes(List<ConditionResult> notes) {
        if (notes.isEmpty()) {
            return this;
        }
        List<ConditionResult> all = new ArrayList<>(this.notes);
        for (ConditionResult note : notes) {
            all.addAll(note.passed ? note.notes : note.getFailures());
        }
        return new ConditionResult(passed, why, condition, failures, Collections.unmodifiableList(all));
    }

    /** This result marked as answered by {@code condition}, unless it already names one. */
    public ConditionResult answeredBy(Condition condition) {
        if (this.condition != null || this == PASS) {
            return this;
        }
        return new ConditionResult(passed, why, condition, failures, notes);
    }

    public boolean isPassed() {
        return passed;
    }

    /** Why it failed - every child failure, one per line, for a composition; null when it passed. */
    public @Nullable FancyText getWhy() {
        return why;
    }

    /** Who answered; null for a bare result nobody marked. */
    public @Nullable Condition getCondition() {
        return condition;
    }

    /** The leaf failures behind this result, in order: itself for a failed leaf, its children's for a composition, none when passed. */
    public List<ConditionResult> getFailures() {
        if (passed) {
            return Collections.emptyList();
        }
        return failures.isEmpty() ? Collections.singletonList(this) : failures;
    }

    /** Failures reported without denying (an {@code optional} child that failed). */
    public List<ConditionResult> getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return passed ? "passed" : "failed: " + (why == null ? "?" : why.toPlainText());
    }
}
