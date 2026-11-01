package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A dialogue option: a text, the {@link Gate} that decides whether the viewer may pick it, and the effects picking it
 * runs. A failing gate draws it disabled, with the reason; picking it pays the gate and runs the effects - the same
 * {@link ClickRule} any button can carry.
 */
public class Choice extends Button {

    private final FancyText text;
    private final Gate gate;
    private final List<Effect> effects;

    public Choice(@Nonnull Screen<?> screen, @Nonnull String id, @Nonnull SlotSet slots, @Nonnull Icon template,
                  @Nonnull FancyText text, @Nonnull Gate gate, @Nonnull List<Effect> effects) {
        super(screen, id, slots, template);
        setKind(ElementKind.CHOICE);
        this.text = text;
        this.gate = gate;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
        rule(ClickRule.on().gate(gate).onSuccess(this.effects.toArray(new Effect[0])));
        dependsOn(gate.getConditions().toArray(new Condition[0]));
    }

    /**
     * Hands the pick to {@code pick} instead of paying the gate and running the effects here - for an engine (a dialogue
     * session) that pays the gate itself, between its own event and its own effects. The gate still draws the choice
     * disabled with every failure.
     */
    @Nonnull
    public Choice pickedBy(@Nonnull Consumer<ClickContext> pick) {
        clearRules();
        onClick(pick);
        return this;
    }

    @Nonnull
    public FancyText getText() {
        return text;
    }

    @Nonnull
    public Gate getGate() {
        return gate;
    }

    @Nonnull
    public List<Effect> getEffects() {
        return effects;
    }

    /** Every failure, one per line - a renderer draws each on a line of its own. */
    private static String reasonsOf(GateResult result) {
        List<String> reasons = new ArrayList<>();
        for (ConditionResult failure : result.getFailures()) {
            if (failure.getWhy() != null && !failure.getWhy().getText().isEmpty()) {
                reasons.add(failure.getWhy().getText());
            }
        }
        return String.join("\n", reasons);
    }

    @Nonnull
    @Override
    Paint decorate(@Nonnull Paint paint, @Nonnull ScreenView view) {
        GateResult result = gate.check(view.newExecutionContext().build());
        return result.isPassed() ? paint : paint.disabled(reasonsOf(result));
    }

}
