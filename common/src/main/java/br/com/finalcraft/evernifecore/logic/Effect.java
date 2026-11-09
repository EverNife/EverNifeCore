package br.com.finalcraft.evernifecore.logic;

import java.math.BigDecimal;

/**
 * A change a definition makes: a message, a command, a value set. {@link Effects#apply} is what applies one, on its
 * target's thread; an effect never applies itself on another thread.
 */
public interface Effect {

    void apply(ExecutionContext ctx);

    default EffectScope getScope() {
        return EffectScope.PERMANENT;
    }

    /** Whether the effect can be held ({@code scope: held}) and given back. */
    default boolean isHoldable() {
        return false;
    }

    /** Whether the {@code as: player|console} modifier means something to it (a command does). */
    default boolean acceptsSender() {
        return false;
    }

    /** Whether the {@code to: <destination>} modifier means something to it (an effect that gives an item or an amount does). */
    default boolean acceptsDestination() {
        return false;
    }

    /**
     * Whether, run with no player and the absent player as its owner, it decides by itself what an absence means (a command
     * has {@code offline:}). A delayed effect whose player left then runs so; any other waits or is dropped by the {@link DeferredEffects}.
     */
    default boolean decidesAbsence() {
        return false;
    }

    /**
     * This effect with its amount multiplied by {@code share}, to split it among several players; one without an amount
     * goes whole, which is this default.
     */
    default Effect withShare(BigDecimal share) {
        return this;
    }
}
