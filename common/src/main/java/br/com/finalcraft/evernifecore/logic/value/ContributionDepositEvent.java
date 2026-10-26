package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.math.BigDecimal;

/** A deposit counted toward a {@link Contribution}: posted after it, on the depositor's thread, only when somebody listens. */
public class ContributionDepositEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ContributionDepositEvent.class);
    }

    private final Contribution<?> contribution;
    private final ExecutionContext context;
    private final BigDecimal amount;
    private final BigDecimal total;

    public ContributionDepositEvent(Contribution<?> contribution, ExecutionContext context, BigDecimal amount, BigDecimal total) {
        this.contribution = contribution;
        this.context = context;
        this.amount = amount;
        this.total = total;
    }

    public Contribution<?> getContribution() {
        return contribution;
    }

    /** The depositor's context: its player, its caller. */
    public ExecutionContext getContext() {
        return context;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
