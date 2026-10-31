package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

/** A trade went through: posted once, after it, on the trading thread, only when somebody listens. Never for a failure. */
public class TransactionEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(TransactionEvent.class);
    }

    private final ExecutionContext context;
    private final TransactionResult result;

    public TransactionEvent(ExecutionContext context, TransactionResult result) {
        this.context = context;
        this.result = result;
    }

    public ExecutionContext getContext() {
        return context;
    }

    public TransactionResult getResult() {
        return result;
    }
}
