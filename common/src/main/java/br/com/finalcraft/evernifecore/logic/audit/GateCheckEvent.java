package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;

/** A gate was checked: every condition and cost tested, nothing taken. */
public class GateCheckEvent extends LogicEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GateCheckEvent.class);
    }

    private final GateResult result;

    public GateCheckEvent(ExecutionContext context, GateResult result) {
        super(context);
        this.result = result;
    }

    public GateResult getResult() {
        return result;
    }
}
