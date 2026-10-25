package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;

/** A gate's payment ended with every cost taken. */
public class GatePaidEvent extends LogicEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GatePaidEvent.class);
    }

    private final GateResult result;

    public GatePaidEvent(ExecutionContext context, GateResult result) {
        super(context);
        this.result = result;
    }

    public GateResult getResult() {
        return result;
    }
}
