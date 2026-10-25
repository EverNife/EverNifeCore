package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.CostRefundEvent;
import br.com.finalcraft.evernifecore.logic.audit.CostTakeEvent;
import br.com.finalcraft.evernifecore.logic.audit.GateCheckEvent;
import br.com.finalcraft.evernifecore.logic.audit.GateDeniedEvent;
import br.com.finalcraft.evernifecore.logic.audit.GatePaidEvent;
import br.com.finalcraft.evernifecore.logic.audit.GatePrePayEvent;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Conditions to meet and costs to pay. {@link #check} tests everything and lists every failure; {@link #pay} checks,
 * then takes the costs in order and, when one does not come out, refunds the taken ones in reverse. There is no lock:
 * each cost's {@code take} is atomic on its own. Both run on the caller's thread. Immutable.
 */
public final class Gate {

    private static final Gate OPEN = new Gate(Collections.<Condition>emptyList(), Collections.<Cost>emptyList());

    private final List<Condition> conditions;
    private final List<Cost> costs;

    private Gate(List<Condition> conditions, List<Cost> costs) {
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
        this.costs = Collections.unmodifiableList(new ArrayList<>(costs));
    }

    public static Gate of(List<Condition> conditions, List<Cost> costs) {
        return new Gate(conditions, costs);
    }

    /** A gate with nothing to meet or pay. */
    public static Gate open() {
        return OPEN;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reads {@code {require: [...], cost: [...]}} (both optional) as a gate of {@code caller}; {@code zone} is the zone of
     * a window written without one. A condition or cost whose type is not registered yet stays pending and fails with
     * the cause until it registers.
     *
     * @throws TypeRegistryException when the node is not an object, has another key, or a definition does not read
     */
    public static Gate parse(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        if (node == null || node.isNull()) {
            return OPEN;
        }
        if (!node.isObject()) {
            throw new TypeRegistryException("A gate is written {require: [<condition>...], cost: [<cost>...]} - this one is " + node + ".");
        }
        Iterator<String> keys = node.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.equals("require") && !key.equals("cost")) {
                throw new TypeRegistryException("A gate has no '" + key + "': it reads require (the conditions) and cost (the costs) - " + node + ".");
            }
        }
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode each : list(node.get("require"))) {
            conditions.add(Conditions.parse(caller, each, zone));
        }
        List<Cost> costs = new ArrayList<>();
        for (JsonNode each : list(node.get("cost"))) {
            costs.add(Costs.parse(caller, each, zone));
        }
        return new Gate(conditions, costs);
    }

    private static List<JsonNode> list(JsonNode node) {
        List<JsonNode> items = new ArrayList<>();
        if (node == null || node.isNull()) {
            return items;
        }
        if (node.isArray()) {
            node.forEach(items::add);
        } else {
            items.add(node);
        }
        return items;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<Cost> getCosts() {
        return costs;
    }

    /** The union of every condition's and cost's sources. */
    public Set<EventSource> getNeeds() {
        Set<EventSource> union = new LinkedHashSet<>();
        for (Condition condition : conditions) {
            union.addAll(condition.getNeeds());
        }
        for (Cost cost : costs) {
            union.addAll(cost.getNeeds());
        }
        return union;
    }

    /** Tests every condition and every cost (without taking); PASSED or DENIED with every failure. Posts {@link GateCheckEvent}. */
    public GateResult check(ExecutionContext ctx) {
        List<ConditionResult> failures = new ArrayList<>();
        List<ConditionResult> notes = new ArrayList<>();
        List<Condition> all = new ArrayList<Condition>(conditions);
        all.addAll(costs);
        for (Condition condition : all) {
            ConditionResult result = Conditions.test(condition, ctx);
            notes.addAll(result.getNotes());
            for (ConditionResult failure : result.getFailures()) {
                failures.add(failure);
                ctx.getAudit().add(ctx, AuditKind.CONDITION_FAILED, failure.getCondition() + ": " + plain(failure.getWhy()));
            }
        }
        GateResult result = new GateResult(this, ctx, failures.isEmpty() ? GateResult.Outcome.PASSED : GateResult.Outcome.DENIED,
                failures, notes);
        ECEventBus.global().postIfListened(GateCheckEvent.class, () -> new GateCheckEvent(ctx, result));
        return result;
    }

    /**
     * {@link #check}; when it passed, posts the cancellable {@link GatePrePayEvent}, then takes each cost in order. A take
     * that answers false or throws refunds every taken cost in reverse order; a refund that throws is audited as
     * {@code REFUND_FAILED} and logged severe on the caller's log, since that cost needs giving back by hand. Ends with
     * {@link GatePaidEvent} or {@link GateDeniedEvent}.
     */
    public GateResult pay(ExecutionContext ctx) {
        GateResult checked = check(ctx);
        if (!checked.isPassed()) {
            return denied(ctx, checked);
        }
        GatePrePayEvent prePay = ECEventBus.global().postIfListened(GatePrePayEvent.class, () -> new GatePrePayEvent(ctx, this, checked));
        if (prePay != null && prePay.isCancelled()) {
            return denied(ctx, checked.as(GateResult.Outcome.DENIED, Collections.singletonList(
                    ConditionResult.fail(FancyText.of("The payment was cancelled by a GatePrePayEvent listener.")))));
        }
        List<Cost> taken = new ArrayList<>();
        for (Cost cost : costs) {
            boolean took;
            Throwable threw = null;
            try {
                took = cost.take(ctx);
            } catch (RuntimeException failure) {
                took = false;
                threw = failure;
            }
            boolean finalTook = took;
            ECEventBus.global().postIfListened(CostTakeEvent.class, () -> new CostTakeEvent(ctx, cost, finalTook));
            if (took) {
                taken.add(cost);
                ctx.getAudit().add(ctx, AuditKind.COST_TAKEN, describe(cost, ctx));
                continue;
            }
            refundInReverse(ctx, taken);
            String why = describe(cost, ctx) + " could not be taken" + (threw == null ? "." : ": " + threw.getMessage());
            ConditionResult failure = ConditionResult.fail(FancyText.of(why)).answeredBy(cost);
            return denied(ctx, checked.as(taken.isEmpty() ? GateResult.Outcome.DENIED : GateResult.Outcome.REFUNDED,
                    Collections.singletonList(failure)));
        }
        GateResult paid = checked.as(GateResult.Outcome.PAID, Collections.<ConditionResult>emptyList());
        ctx.getAudit().add(ctx, AuditKind.GATE_PAID, describeAll(ctx));
        ECEventBus.global().postIfListened(GatePaidEvent.class, () -> new GatePaidEvent(ctx, paid));
        return paid;
    }

    private void refundInReverse(ExecutionContext ctx, List<Cost> taken) {
        for (int i = taken.size() - 1; i >= 0; i--) {
            Cost cost = taken.get(i);
            boolean refunded;
            try {
                cost.refund(ctx);
                refunded = true;
                ctx.getAudit().add(ctx, AuditKind.COST_REFUNDED, describe(cost, ctx));
            } catch (RuntimeException failure) {
                refunded = false;
                String text = describe(cost, ctx) + " was taken and its refund failed (" + failure + ") - give it back by hand.";
                ctx.getAudit().add(ctx, AuditKind.REFUND_FAILED, text, null, failure);
                ctx.getCaller().getPlugin().getLog().severe("[" + ctx.getCaller() + "] " + text);
            }
            boolean finalRefunded = refunded;
            ECEventBus.global().postIfListened(CostRefundEvent.class, () -> new CostRefundEvent(ctx, cost, finalRefunded));
        }
    }

    private static GateResult denied(ExecutionContext ctx, GateResult result) {
        ctx.getAudit().add(ctx, AuditKind.GATE_DENIED, plain(result.explain()));
        ECEventBus.global().postIfListened(GateDeniedEvent.class, () -> new GateDeniedEvent(ctx, result));
        return result;
    }

    private String describeAll(ExecutionContext ctx) {
        List<String> described = new ArrayList<>();
        for (Cost cost : costs) {
            described.add(describe(cost, ctx));
        }
        return described.isEmpty() ? "nothing to pay" : String.join(", ", described);
    }

    private static String describe(Cost cost, ExecutionContext ctx) {
        try {
            return plain(cost.describe(ctx));
        } catch (RuntimeException failure) {
            return String.valueOf(cost);
        }
    }

    private static String plain(FancyText text) {
        return text == null ? "" : text.toPlainText();
    }

    @Override
    public String toString() {
        return "Gate(require " + conditions + ", cost " + costs + ")";
    }

    public static final class Builder {
        private final List<Condition> conditions = new ArrayList<>();
        private final List<Cost> costs = new ArrayList<>();

        private Builder() {
        }

        public Builder require(Condition... conditions) {
            this.conditions.addAll(Arrays.asList(conditions));
            return this;
        }

        public Builder cost(Cost... costs) {
            this.costs.addAll(Arrays.asList(costs));
            return this;
        }

        public Gate build() {
            return new Gate(conditions, costs);
        }
    }
}
