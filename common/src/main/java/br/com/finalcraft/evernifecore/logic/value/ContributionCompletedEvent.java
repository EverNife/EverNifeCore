package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

/**
 * A {@link Contribution} reached its target: posted once, after the completing deposit's event and before the
 * completion effects apply, only when somebody listens.
 */
public class ContributionCompletedEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ContributionCompletedEvent.class);
    }

    private final Contribution<?> contribution;
    private final ExecutionContext context;

    public ContributionCompletedEvent(Contribution<?> contribution, ExecutionContext context) {
        this.contribution = contribution;
        this.context = context;
    }

    public Contribution<?> getContribution() {
        return contribution;
    }

    /** The completing deposit's context. */
    public ExecutionContext getContext() {
        return context;
    }
}
