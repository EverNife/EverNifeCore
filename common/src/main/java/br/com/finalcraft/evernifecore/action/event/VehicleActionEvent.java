package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.VehicleContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A vehicle - a minecart, a boat - placed, broken or entered: {@code getContext().getAction()} says which. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class VehicleActionEvent extends ActionEvent<VehicleContext> {

    private VehicleActionEvent(VehicleContext context) {
        super(EventSource.VEHICLE, context);
    }

    /** Before it happens, after the regions allowed it: cancelling it cancels the native event. */
    public static final class Pre extends VehicleActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(VehicleContext context) {
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

    /** After it happened. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends VehicleActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(VehicleContext context) {
            super(context);
        }
    }
}
