package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A catch reeled in by a player's rod; the context's item entity is the catch, flying to the player. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class FishActionEvent extends ActionEvent<ItemContext> {

    private FishActionEvent(ItemContext context) {
        super(EventSource.FISH, context);
    }

    /** Before the catch is reeled in, after the regions allowed it: cancelling it leaves the catch in the water. */
    public static final class Pre extends FishActionEvent implements ECCancellable {

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

    /** After the catch was reeled in. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends FishActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ItemContext context) {
            super(context);
        }
    }
}
