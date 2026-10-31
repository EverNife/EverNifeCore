package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;

import java.math.BigDecimal;
import java.util.UUID;

/** Who receives: a member is eligible when every eligibility of the rule accepts it. Any thread. */
public interface Eligibility extends RewardPiece {

    boolean isEligible(UUID member, RewardContext ctx);

    @Override
    default Role getRole() {
        return Role.ELIGIBILITY;
    }

    /** Every candidate: the owner's members, offline included, and whoever contributed (while the leaver keeps). */
    static Eligibility anyMember() {
        return CoreRewardPieces.ANY_MEMBER;
    }

    /** The candidates online when the rule pays. */
    static Eligibility online() {
        return CoreRewardPieces.ONLINE;
    }

    /** The candidates standing in the owner's region or world; for an owner without a place (a group, a player) the online ones. */
    static Eligibility present() {
        return CoreRewardPieces.PRESENT;
    }

    /**
     * {@code leader} is the group's leader; any other name is a {@code GroupRole} the member holds. Only a group owner
     * has roles: {@link Rewards#pay} refuses this piece on any other owner.
     */
    static Eligibility role(String role) {
        return CoreRewardPieces.role(role);
    }

    /** Only {@link RewardContext#getLastActor()}, whose action closed it; nobody when the caller named none. */
    static Eligibility lastActor() {
        return CoreRewardPieces.LAST_ACTOR;
    }

    /** A contribution of at least {@code min}. */
    static Eligibility contributionAtLeast(BigDecimal min) {
        return CoreRewardPieces.contributionAtLeast(min);
    }

    /** A contribution of at least {@code ofFairShare} times the fair share (total / contributors): 0.6 = 60% of it. */
    static Eligibility contributionShare(BigDecimal ofFairShare) {
        return CoreRewardPieces.contributionShare(ofFairShare);
    }

    /** The {@code count} highest contributions (above zero); a tie at the edge goes to who comes first among the candidates. */
    static Eligibility top(int count) {
        return CoreRewardPieces.top(count);
    }

    /** {@code condition} passes for the member: run with the member online, or for its uuid as owner when offline. */
    static Eligibility condition(Condition condition) {
        return CoreRewardPieces.condition(condition);
    }
}
