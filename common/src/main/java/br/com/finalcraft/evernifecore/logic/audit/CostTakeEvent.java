package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

/** A gate tried to take one cost; {@link #isTaken()} says whether it came out. */
public class CostTakeEvent extends LogicEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(CostTakeEvent.class);
    }

    private final Cost cost;
    private final boolean taken;

    public CostTakeEvent(ExecutionContext context, Cost cost, boolean taken) {
        super(context);
        this.cost = cost;
        this.taken = taken;
    }

    public Cost getCost() {
        return cost;
    }

    public boolean isTaken() {
        return taken;
    }
}
