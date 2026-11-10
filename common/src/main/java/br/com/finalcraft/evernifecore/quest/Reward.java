package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.reward.RewardRule;
import br.com.finalcraft.evernifecore.logic.reward.RewardTable;
import br.com.finalcraft.evernifecore.logic.reward.RewardTrigger;

import java.util.List;

/**
 * What a quest pays: its {@code rewards:} list as a {@link RewardTable}, one step per item, paid with the contribution
 * the engine measured. A step pays only at the trigger its {@code moment} takes, so the same table serves completion,
 * the window's end and the owner's end. Immutable.
 */
public final class Reward {

    public static final Reward NONE = new Reward(RewardTable.EMPTY);

    private final RewardTable table;

    private Reward(RewardTable table) {
        this.table = table;
    }

    /** The steps of the quest's {@code rewards:}, in order. */
    public static Reward of(List<RewardRule> steps) {
        return steps.isEmpty() ? NONE : new Reward(RewardTable.of(steps));
    }

    public RewardTable getTable() {
        return table;
    }

    /** Whether any step pays at {@code trigger}. */
    public boolean paysAt(RewardTrigger trigger) {
        return table.paysAt(trigger);
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    @Override
    public String toString() {
        return "Reward" + table.getSteps();
    }
}
