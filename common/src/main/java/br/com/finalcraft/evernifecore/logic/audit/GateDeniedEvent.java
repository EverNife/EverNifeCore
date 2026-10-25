package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;

/** A gate's payment ended denied - its check failed, a listener cancelled it, or a cost did not come out (refunded). */
public class GateDeniedEvent extends LogicEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GateDeniedEvent.class);
    }

    private final GateResult result;

    public GateDeniedEvent(ExecutionContext context, GateResult result) {
        super(context);
        this.result = result;
    }

    public GateResult getResult() {
        return result;
    }
}
