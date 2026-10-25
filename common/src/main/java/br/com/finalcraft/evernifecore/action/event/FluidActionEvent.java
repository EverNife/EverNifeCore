package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.FluidContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A liquid flowing into a block. The costliest event a server raises: subscribing keeps its native listener on. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class FluidActionEvent extends ActionEvent<FluidContext> {

    private FluidActionEvent(FluidContext context) {
        super(EventSource.FLUID, context);
    }

    /** Before the liquid flows, after the regions allowed it: cancelling it stops the flow. */
    public static final class Pre extends FluidActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(FluidContext context) {
            super(context);
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

    /** After the liquid flowed. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends FluidActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(FluidContext context) {
            super(context);
        }
    }
}
