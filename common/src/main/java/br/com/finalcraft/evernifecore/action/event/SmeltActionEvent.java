package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * An item taken out of a furnace by a player; the context's amount is how many were taken. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class SmeltActionEvent extends ActionEvent<ItemContext> {

    private SmeltActionEvent(ItemContext context) {
        super(EventSource.SMELT, context);
    }

    /** No platform raises it today: taking an item out of a furnace cannot be cancelled - {@link br.com.finalcraft.evernifecore.action.ActionSupport#hasPre} answers false. */
    public static final class Pre extends SmeltActionEvent implements ECCancellable {

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

    /** After the items were taken out. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends SmeltActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ItemContext context) {
            super(context);
        }
    }
}
