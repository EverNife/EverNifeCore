package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * Fire spreading to a block on its own; fire lit by someone or by lava is a {@link PlaceActionEvent}. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class FireSpreadActionEvent extends ActionEvent<BlockContext> {

    private FireSpreadActionEvent(BlockContext context) {
        super(EventSource.FIRE_SPREAD, context);
    }

    /** Before the fire spreads, after the regions allowed it: cancelling it stops the spread. */
    public static final class Pre extends FireSpreadActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(BlockContext context) {
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

    /** After the fire spread. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends FireSpreadActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(BlockContext context) {
            super(context);
        }
    }
}
