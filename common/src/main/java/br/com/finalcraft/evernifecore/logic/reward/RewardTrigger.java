package br.com.finalcraft.evernifecore.logic.reward;

/** Why a caller pays now, matched by the rule's {@link Moment}. */
public enum RewardTrigger {
    /** What the reward was for is done. */
    ON_COMPLETE,
    /** Its time window closed. */
    ON_WINDOW_END,
    /** Its owner is gone (a group dissolved): a partial payment. */
    ON_OWNER_GONE
}
