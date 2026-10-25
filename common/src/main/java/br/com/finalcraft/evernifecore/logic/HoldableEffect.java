package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.StateHolder;

/**
 * An effect that can be held: {@link #hold} changes one slot of the player's state for a {@link StateHolder} over
 * {@link HeldPlayerState} - the first holder of the slot saves what the player had, the last to release writes it back,
 * and a quit gives back what is still held. Written in a definition, {@code scope: held} holds it for the holder the
 * consumer put in the context under {@link #HOLDER_ATTRIBUTE} (a flag, a quest stage), or for the caller without one.
 */
public interface HoldableEffect extends Effect {

    /** The {@link StateHolder} a consumer puts in the context to bound what {@code scope: held} holds. */
    String HOLDER_ATTRIBUTE = "evernifecore:holder";

    /** Set by the executor on a {@code scope: held} run: the holder {@link #apply} holds for instead of changing for good. */
    String HELD_BY_ATTRIBUTE = "evernifecore:held-by";

    /** Holds the effect's slot on the context's player for {@code holder}; on the player's world thread. */
    HeldEffect hold(ExecutionContext ctx, StateHolder holder);

    @Override
    default boolean isHoldable() {
        return true;
    }

    /** What {@link #hold} took; releasing it twice does nothing the second time. */
    interface HeldEffect {
        /** @return whether the slot was given back - false while another holder still holds it */
        boolean release();
    }
}
