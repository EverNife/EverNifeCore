package br.com.finalcraft.evernifecore.logic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Effects run in order; one that fails is audited and the rest still run. An effect itself. Immutable. */
public final class EffectList implements Effect {

    private final List<Effect> effects;

    EffectList(List<Effect> effects) {
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }

    public List<Effect> getEffects() {
        return effects;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    /** Runs each effect here, in order - call it through {@link Effects#apply}, which puts it on the target's thread. */
    @Override
    public void apply(ExecutionContext ctx) {
        for (Effect effect : effects) {
            Effects.execute(ctx, effect);
        }
    }

    @Override
    public Effect withShare(BigDecimal share) {
        List<Effect> shared = new ArrayList<>(effects.size());
        for (Effect effect : effects) {
            shared.add(effect.withShare(share));
        }
        return new EffectList(shared);
    }

    @Override
    public String toString() {
        return effects.toString();
    }
}
