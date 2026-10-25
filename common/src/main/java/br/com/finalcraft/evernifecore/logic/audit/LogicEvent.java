package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A gate was checked or paid, a cost taken or refunded, an effect applied. Posted only when somebody listens, on the
 * thread that ran the gate or the effect; a subscriber that cares about one plugin filters by {@link #getCaller()}.
 */
public abstract class LogicEvent extends ECEvent implements IECEvent {

    private final ExecutionContext context;

    protected LogicEvent(ExecutionContext context) {
        this.context = Objects.requireNonNull(context, "'context' cannot be null when building a logic event!");
    }

    public ExecutionContext getContext() {
        return context;
    }

    public Caller getCaller() {
        return context.getCaller();
    }

    public ProgressOwner getOwner() {
        return context.getOwner();
    }

    public @Nullable FPlayer getPlayer() {
        return context.getPlayer();
    }
}
