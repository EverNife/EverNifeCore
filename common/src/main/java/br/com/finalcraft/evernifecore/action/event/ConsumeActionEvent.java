package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * An item a player eats or drinks. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class ConsumeActionEvent extends ActionEvent<ItemContext> {

    private ConsumeActionEvent(ItemContext context) {
        super(EventSource.CONSUME, context);
    }

    /** Before the item is consumed, after the regions allowed it: cancelling it keeps the item. */
    public static final class Pre extends ConsumeActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(ItemContext context) {
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

    /** After the item was consumed. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends ConsumeActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ItemContext context) {
            super(context);
        }
    }
}
