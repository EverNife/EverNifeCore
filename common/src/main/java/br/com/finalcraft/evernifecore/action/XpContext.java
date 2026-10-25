package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

/** Experience a player picked up ({@link EventSource#XP_GAIN}); {@link #getActor()} is that player. */
public final class XpContext extends ActionContext {

    private final int amount;

    /**
     * @param position where the player stands
     * @param amount   the experience points gained
     */
    public XpContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, int amount) {
        super(EventSource.XP_GAIN, position, actor, nativeEvent);
        this.amount = amount;
    }

    /** The experience points gained. */
    public int getAmount() {
        return amount;
    }
}
