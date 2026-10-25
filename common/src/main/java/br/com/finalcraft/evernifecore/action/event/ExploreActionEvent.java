package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ExploreContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A player stepping into another chunk. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class ExploreActionEvent extends ActionEvent<ExploreContext> {

    private ExploreActionEvent(ExploreContext context) {
        super(EventSource.EXPLORE, context);
    }

    /** Before the step, after the regions allowed it: cancelling it keeps the player in the chunk he was in. Hytale has no such point: the step is seen after it happened. */
    public static final class Pre extends ExploreActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(ExploreContext context) {
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

    /** After the player stepped into the other chunk. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends ExploreActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ExploreContext context) {
            super(context);
        }
    }
}
