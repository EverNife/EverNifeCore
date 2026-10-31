package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How much each eligible member gets, as a share the rule's effects are multiplied by ({@code Effect.withShare}). The
 * shares of a split sum to at most 1 (a share is cut, never rounded up, so the total paid never passes the reward);
 * {@link #each()} gives 1 to everyone. A member left out of the map gets nothing. Any thread.
 */
public interface Distribution extends RewardPiece {

    /** The share of each member of {@code eligible}, in the order to pay; read-only or fresh. */
    Map<UUID, BigDecimal> sharesOf(Set<UUID> eligible, RewardContext ctx);

    @Override
    default Role getRole() {
        return Role.DISTRIBUTION;
    }

    /** The whole reward to every eligible member. */
    static Distribution each() {
        return CoreRewardPieces.EACH;
    }

    /** The reward split equally among the eligible. */
    static Distribution splitEqual() {
        return CoreRewardPieces.SPLIT_EQUAL;
    }

    /** The reward split by contribution; nobody gets anything when the eligible contributed nothing. */
    static Distribution proportional() {
        return CoreRewardPieces.PROPORTIONAL;
    }

    /** The whole reward to the highest contribution among the eligible. */
    static Distribution topTakesAll() {
        return CoreRewardPieces.TOP_TAKES_ALL;
    }

    /** {@code byPosition[i]} of the reward to the i-th highest contribution; positions past the list get nothing. */
    static Distribution tiers(BigDecimal... byPosition) {
        return CoreRewardPieces.tiers(byPosition);
    }
}
