package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

/** A gate gave one taken cost back because a later one did not come out; {@link #isRefunded()} is false when the refund threw. */
public class CostRefundEvent extends LogicEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(CostRefundEvent.class);
    }

    private final Cost cost;
    private final boolean refunded;

    public CostRefundEvent(ExecutionContext context, Cost cost, boolean refunded) {
        super(context);
        this.cost = cost;
        this.refunded = refunded;
    }

    public Cost getCost() {
        return cost;
    }

    public boolean isRefunded() {
        return refunded;
    }
}
