package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A block placed - by a player, a mob setting it, or fire lit by someone or by lava. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class PlaceActionEvent extends ActionEvent<BlockContext> {

    private PlaceActionEvent(BlockContext context) {
        super(EventSource.PLACE, context);
    }

    /** Before the block is set, after the regions allowed it: cancelling it cancels the native event. */
    public static final class Pre extends PlaceActionEvent implements ECCancellable {

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

    /** After the block was set. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends PlaceActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(BlockContext context) {
            super(context);
        }
    }
}
