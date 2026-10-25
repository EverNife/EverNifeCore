package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.util.Objects;

/**
 * An effect is about to apply, on its target's thread. Cancelling it skips that effect only; {@link #setEffect}
 * replaces what runs (a listener that scales a reward hands back a scaled effect).
 */
public class EffectApplyEvent extends LogicEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(EffectApplyEvent.class);
    }

    private volatile Effect effect;
    private volatile boolean cancelled;

    public EffectApplyEvent(ExecutionContext context, Effect effect) {
        super(context);
        this.effect = effect;
    }

    /** What will run - the original, or what a listener set. */
    public Effect getEffect() {
        return effect;
    }

    public void setEffect(Effect effect) {
        this.effect = Objects.requireNonNull(effect, "'effect' cannot be null: cancel the event to skip the effect instead.");
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
