package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Nonnull
    @Override
    Paint decorate(@Nonnull Paint paint, @Nonnull ScreenView view) {
        GateResult result = gate.check(view.newExecutionContext().build());
        return result.isPassed() ? paint : paint.disabled(ClickRule.missingOf(result));
    }

}
