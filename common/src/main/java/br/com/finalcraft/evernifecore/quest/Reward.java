package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.reward.RewardRule;
import br.com.finalcraft.evernifecore.logic.reward.RewardTrigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a quest pays: its {@code rewards:} list, one {@link RewardRule} per item, each paid by {@code Rewards.pay} with
 * the contribution the engine measured. A rule pays only at the trigger its {@code moment} takes, so the same list
 * serves completion, the window's end and the owner's end. Immutable.
 */
public final class Reward {

    public static final Reward NONE = new Reward(Collections.<RewardRule>emptyList());

    private final List<RewardRule> rules;

    private Reward(List<RewardRule> rules) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    public static Reward of(List<RewardRule> rules) {
        return rules.isEmpty() ? NONE : new Reward(rules);
    }

    public List<RewardRule> getRules() {
        return rules;
    }

    /** Whether any rule pays at {@code trigger}. */
    public boolean paysAt(RewardTrigger trigger) {
        for (RewardRule rule : rules) {
            if (rule.getMoment().paysAt(trigger)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    @Override
    public String toString() {
        return "Reward" + rules;
    }
}
