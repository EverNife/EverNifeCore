package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;

/** When a rule pays: {@link Rewards#pay} with a trigger the moment does not take pays nothing. Any thread. */
public interface Moment extends RewardPiece {

    boolean paysAt(RewardTrigger trigger);

    @Override
    default Role getRole() {
        return Role.MOMENT;
    }

    static Moment onComplete() {
        return CoreRewardPieces.ON_COMPLETE;
    }

    static Moment onWindowEnd() {
        return CoreRewardPieces.ON_WINDOW_END;
    }

    static Moment onOwnerGone() {
        return CoreRewardPieces.ON_OWNER_GONE;
    }
}
