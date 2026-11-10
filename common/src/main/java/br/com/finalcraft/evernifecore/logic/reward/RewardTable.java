package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A reward as an ordered list of steps, each a {@link RewardRule}: the one way a reward is paid. {@link #pay} pays the
 * steps in order and answers a {@link RewardReceipt}; {@link #preview} answers the same receipt applying nothing.
 * A plugin keys tables by phase with {@link PhasedRewardTable}. Immutable; any thread.
 */
public final class RewardTable {

    /** No step: pays nothing. */
    public static final RewardTable EMPTY = new RewardTable(Collections.<RewardRule>emptyList());

    private final List<RewardRule> steps;

    private RewardTable(List<RewardRule> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /** The steps, in the order they pay. */
    public static RewardTable of(List<RewardRule> steps) {
        return steps.isEmpty() ? EMPTY : new RewardTable(steps);
    }

    /**
     * Reads a list of steps, each as {@link RewardRule#parse(ECPluginData, RewardOccasion, JsonNode)} reads one; a single
     * step may be written without the list, and nothing (null) is {@link #EMPTY}. An effect with a {@code target} other
     * than {@code self} in a step that is not {@code once} logs a warning naming the step by its index and {@code name}.
     *
     * @throws TypeRegistryException as the step's reading does, prefixed with the step's index and name
     */
    public static RewardTable parse(ECPluginData caller, RewardOccasion occasion, JsonNode node) throws TypeRegistryException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return EMPTY;
        }
        List<JsonNode> written = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(written::add);
        } else {
            written.add(node);
        }
        List<RewardRule> steps = new ArrayList<>();
        for (int i = 0; i < written.size(); i++) {
            try {
                steps.add(RewardRule.parseStep(caller, occasion, written.get(i), i));
            } catch (TypeRegistryException refused) {
                throw new TypeRegistryException(RewardRule.labelOf(i, written.get(i).path("name").asText(null)) + ": " + refused.getMessage());
            }
        }
        return of(steps);
    }

    /** In the order they pay. Read-only. */
    public List<RewardRule> getSteps() {
        return steps;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Whether any step pays at {@code trigger}. */
    public boolean paysAt(RewardTrigger trigger) {
        for (RewardRule step : steps) {
            if (step.getMoment().paysAt(trigger)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pays every step for {@code ctx}, one after the other: a step starts once the previous one applied or kept all its
     * effects. Each step pays as {@link Rewards#payStep} describes. Any thread.
     *
     * @return completes with the receipt of every step, in order
     * @throws IllegalArgumentException before anything is paid, when a step that pays now asks for a group role and the
     *                                  owner is not a group
     */
    public CompletableFuture<RewardReceipt> pay(RewardContext ctx) {
        checkRoles(ctx);
        CompletableFuture<List<RewardReceipt.Step>> paid = CompletableFuture.completedFuture(new ArrayList<RewardReceipt.Step>());
        for (int i = 0; i < steps.size(); i++) {
            int index = i;
            paid = paid.thenCompose(done -> Rewards.payStep(steps.get(index), ctx, index).thenApply(step -> {
                done.add(step);
                return done;
            }));
        }
        return paid.thenApply(RewardReceipt::new);
    }

    /**
     * The receipt {@link #pay} would answer for {@code ctx}, applying nothing: no effect, no pending delivery, no
     * {@link RewardPaidEvent}, no audit line. Who is online is read now. Any thread.
     *
     * @throws IllegalArgumentException as {@link #pay} does
     */
    public RewardReceipt preview(RewardContext ctx) {
        checkRoles(ctx);
        List<RewardReceipt.Step> settled = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            settled.add(Rewards.settle(steps.get(i), ctx, i));
        }
        return new RewardReceipt(settled);
    }

    private void checkRoles(RewardContext ctx) {
        for (RewardRule step : steps) {
            if (step.getMoment().paysAt(ctx.getTrigger())) {
                Rewards.checkRoles(step, ctx);
            }
        }
    }

    @Override
    public String toString() {
        return "RewardTable" + steps;
    }
}
