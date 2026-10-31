package br.com.finalcraft.evernifecore.logic.reward;

/**
 * One piece of a {@link RewardRule}. Every role lives in the one {@link Rewards#registry() "reward-piece"} registry; the
 * piece names its role, and {@link RewardRule#parse} refuses a piece written under another role's key. Implementations
 * are immutable and answer from any thread.
 */
public interface RewardPiece {

    /** Which part of a rule a piece is: who receives, how much each, the absent, when, and leavers/joiners. */
    enum Role { ELIGIBILITY, DISTRIBUTION, ABSENCE, MOMENT, MEMBERSHIP }

    Role getRole();
}
