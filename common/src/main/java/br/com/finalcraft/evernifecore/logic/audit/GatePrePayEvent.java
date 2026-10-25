package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;

/** A gate passed its check and is about to take its first cost; cancelling it denies the payment before any take. */
public class GatePrePayEvent extends LogicEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GatePrePayEvent.class);
    }

    private final Gate gate;
    private final GateResult checked;
    private volatile boolean cancelled;

    public GatePrePayEvent(ExecutionContext context, Gate gate, GateResult checked) {
        super(context);
        this.gate = gate;
        this.checked = checked;
    }

    public Gate getGate() {
        return gate;
    }

    /** The passed check this payment follows. */
    public GateResult getChecked() {
        return checked;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
