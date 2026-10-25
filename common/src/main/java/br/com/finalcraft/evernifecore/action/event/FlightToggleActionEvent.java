package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A player starting or stopping flight. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class FlightToggleActionEvent extends ActionEvent<SimpleContext> {

    private FlightToggleActionEvent(SimpleContext context) {
        super(EventSource.FLIGHT_TOGGLE, context);
    }

    /** Before the toggle, after {@code force-fly} had its say: cancelling it keeps the player as they were. */
    public static final class Pre extends FlightToggleActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(SimpleContext context) {
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

    /** After the toggle. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends FlightToggleActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(SimpleContext context) {
            super(context);
        }
    }
}
