package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What one gesture on a button does: a {@link Gate} that must pass - and is paid - and the effects of the success and of
 * the denial. {@code ClickRule.on(PRIMARY).gate(price).onSuccess(give).onDeny(tell)}; a button holding several runs
 * the first that answers the gesture ({@link #dispatch}).
 *
 * <p>The denial runs with the {@link GateResult} - every failure, not the first - as the context attribute
 * {@code GateResult.class}'s name and its explanation as the variable {@code missing}, so a denial text reads
 * {@code {missing}}. Checking and taking run on the clicking thread without I/O, as the gate promises; the effects
 * dispatch as {@link Effects#apply} decides.</p>
 */
public final class ClickRule {

    /** The context attribute a denial reads the whole {@link GateResult} from. */
    public static final String GATE_RESULT = GateResult.class.getName();

    public enum Outcome {
        /** No rule answered the gesture. */
        SKIPPED,
        DENIED,
        SUCCESS
    }

    private final Set<ClickGesture> gestures;
    private final Gate gate;
    private final List<Effect> onSuccess;
    private final List<Effect> onDeny;

    private ClickRule(Set<ClickGesture> gestures, Gate gate, List<Effect> onSuccess, List<Effect> onDeny) {
        this.gestures = gestures;
        this.gate = gate;
        this.onSuccess = onSuccess;
        this.onDeny = onDeny;
    }

    /** A rule answering {@code gestures}; none means every gesture. Open gate and no effect until said otherwise. */
    @Nonnull
    public static ClickRule on(@Nonnull ClickGesture... gestures) {
        Set<ClickGesture> answered = gestures.length == 0 ? EnumSet.noneOf(ClickGesture.class) : EnumSet.copyOf(Arrays.asList(gestures));
        return new ClickRule(Collections.unmodifiableSet(answered), Gate.open(), Collections.<Effect>emptyList(),
                Collections.<Effect>emptyList());
    }

    @Nonnull
    public ClickRule gate(@Nonnull Gate gate) {
        return new ClickRule(gestures, gate, onSuccess, onDeny);
    }

    @Nonnull
    public ClickRule onSuccess(@Nonnull Effect... effects) {
        return new ClickRule(gestures, gate, append(onSuccess, effects), onDeny);
    }

    @Nonnull
    public ClickRule onDeny(@Nonnull Effect... effects) {
        return new ClickRule(gestures, gate, onSuccess, append(onDeny, effects));
    }

    private static List<Effect> append(List<Effect> current, Effect[] more) {
        List<Effect> all = new ArrayList<>(current);
        all.addAll(Arrays.asList(more));
        return Collections.unmodifiableList(all);
    }

    /** Runs the first rule of {@code rules} that answers the click's gesture, and only it. */
    @Nonnull
    public static Outcome dispatch(@Nonnull Collection<ClickRule> rules, @Nonnull ClickContext context) {
        for (ClickRule rule : rules) {
            if (rule.respondsTo(context.getGesture())) {
                return rule.run(context.newExecutionContext().build());
            }
        }
        return Outcome.SKIPPED;
    }

    public boolean respondsTo(@Nonnull ClickGesture gesture) {
        return gestures.isEmpty() || gestures.contains(gesture);
    }

    /** Pays the gate in {@code ctx}; on success runs the success effects, on denial the deny ones with the result in hand. */
    @Nonnull
    public Outcome run(@Nonnull ExecutionContext ctx) {
        GateResult result = gate.pay(ctx);
        if (result.isPassed()) {
            applyAll(ctx, onSuccess);
            return Outcome.SUCCESS;
        }
        ExecutionContext denied = ctx.derive()
                .attribute(GATE_RESULT, result)
                .variable("missing", missingOf(result))
                .build();
        applyAll(denied, onDeny);
        return Outcome.DENIED;
    }

    /** Every failure of {@code result} in plain words, comma separated - what {@code {missing}} reads. */
    @Nonnull
    public static String missingOf(@Nonnull GateResult result) {
        List<String> parts = new ArrayList<>();
        for (ConditionResult failure : result.getFailures()) {
            if (failure.getWhy() != null && !failure.getWhy().getText().isEmpty()) {
                parts.add(failure.getWhy().getText());
            }
        }
        return String.join(", ", parts);
    }

    private static void applyAll(ExecutionContext ctx, List<Effect> effects) {
        for (Effect effect : effects) {
            Effects.apply(ctx, effect);
        }
    }

    /** What the gate costs, in the viewer's words - what a button's lore reads as {@code {cost}}. Charges nothing. */
    @Nonnull
    public String describeCost(@Nonnull ExecutionContext preview) {
        return gate.check(preview).describeCosts();
    }

    @Nonnull
    public Set<ClickGesture> getGestures() {
        return gestures;
    }

    @Nonnull
    public Gate getGate() {
        return gate;
    }

    @Nonnull
    public List<Effect> getOnSuccess() {
        return onSuccess;
    }

    @Nonnull
    public List<Effect> getOnDeny() {
        return onDeny;
    }

    @Override
    public String toString() {
        return "ClickRule{" + (gestures.isEmpty() ? "any" : gestures) + ", " + gate + "}";
    }

}
