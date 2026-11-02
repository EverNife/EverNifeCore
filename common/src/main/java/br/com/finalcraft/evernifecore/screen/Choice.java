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
 * runs. Each redraw checks the gate once: a pass draws it with what it costs as the last lore line, a failure draws it
 * disabled with every reason (or not at all, {@link #hiddenUntilPassed()}); a name with no colour of its own reads
 * green or grey to match. Picking it pays the gate and runs the effects - the same {@link ClickRule} any button can
 * carry.
 */
public class Choice extends Button {

    private final FancyText text;
    private final Gate gate;
    private final List<Effect> effects;
    private boolean hiddenUntilPassed = false;

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

    /** Leaves it undrawn while its gate fails - an option the viewer learns of only once they may pick it. */
    @Nonnull
    public Choice hiddenUntilPassed() {
        this.hiddenUntilPassed = true;
        return this;
    }

    public boolean isHiddenUntilPassed() {
        return hiddenUntilPassed;
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

    @Override
    void draw(@Nonnull SlotWriter writer, @Nonnull Paint base, @Nonnull ScreenView view, @Nonnull Icon icon) {
        //the one check of this redraw decides all three: undrawn, disabled with its reasons, enabled with its costs
        GateResult result = gate.check(view.newExecutionContext().build());
        if (!result.isPassed() && hiddenUntilPassed) {
            return;
        }
        Icon drawn = icon.copy();
        drawn.colourUncolouredName(result.isPassed() ? "§a" : "§7");
        if (!result.isPassed()) {
            writer.paint(base.disabled(reasonsOf(result)), getSlots(), drawn);
            return;
        }
        String costs = result.describeCosts();
        if (!costs.isEmpty()) {
            drawn.appendTrailingLore("§7" + costs);
        }
        writer.paint(base, getSlots(), drawn);
    }

}
