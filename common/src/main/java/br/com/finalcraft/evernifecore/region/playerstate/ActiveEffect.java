package br.com.finalcraft.evernifecore.region.playerstate;

import br.com.finalcraft.evernifecore.region.flag.EffectLevel;

import java.time.Duration;
import java.util.Objects;

/** An effect a player has now, with the time it has left. */
public final class ActiveEffect {

    private final EffectLevel effect;
    private final Duration remaining;

    public ActiveEffect(EffectLevel effect, Duration remaining) {
        this.effect = effect;
        this.remaining = remaining;
    }

    public EffectLevel getEffect() {
        return effect;
    }

    public Duration getRemaining() {
        return remaining;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActiveEffect && effect.equals(((ActiveEffect) other).effect) && remaining.equals(((ActiveEffect) other).remaining);
    }

    @Override
    public int hashCode() {
        return Objects.hash(effect, remaining);
    }

    @Override
    public String toString() {
        return effect + " for " + remaining;
    }
}
