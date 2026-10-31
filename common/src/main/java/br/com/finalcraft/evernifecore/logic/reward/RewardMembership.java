package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;

/**
 * The YAML {@code leaver} / {@code joiner}: whether a contributor who is no longer a member stays a candidate, and
 * whether a member who joined late inherits anything. A contribution only starts when the member joined, so both
 * joiner modes pay the same; the flag is kept for a caller that measures otherwise. Any thread.
 */
public interface RewardMembership extends RewardPiece {

    /** {@code leaver: keeps} (true) or {@code forfeits}. */
    boolean leaverKeeps();

    /** {@code joiner: inherits-zero} (true) or {@code from-now}. */
    boolean joinerInherits();

    @Override
    default Role getRole() {
        return Role.MEMBERSHIP;
    }

    static RewardMembership of(boolean leaverKeeps, boolean joinerInherits) {
        return CoreRewardPieces.membership(leaverKeeps, joinerInherits);
    }
}
