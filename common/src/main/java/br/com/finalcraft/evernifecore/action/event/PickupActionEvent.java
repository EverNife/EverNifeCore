package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * An item picked up from the ground, by a player or a mob. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class PickupActionEvent extends ActionEvent<ItemContext> {

    private PickupActionEvent(ItemContext context) {
        super(EventSource.ITEM_PICKUP, context);
    }

    /** Before the pickup, after the regions allowed it: cancelling it leaves the item on the ground. */
    public static final class Pre extends PickupActionEvent implements ECCancellable {

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

    /** After the item was picked up. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends PickupActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ItemContext context) {
            super(context);
        }
    }
}
