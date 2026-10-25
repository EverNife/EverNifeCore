package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * An item crafted by a player; the context's amount is the whole amount produced, a shift-click included. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class CraftActionEvent extends ActionEvent<ItemContext> {

    private CraftActionEvent(ItemContext context) {
        super(EventSource.CRAFT, context);
    }

    /** Before the craft, after the regions allowed it: cancelling it cancels the craft. */
    public static final class Pre extends CraftActionEvent implements ECCancellable {

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

    /** After the items were crafted. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends CraftActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ItemContext context) {
            super(context);
        }
    }
}
