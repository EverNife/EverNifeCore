package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ExplosionContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * An explosion as a whole; each block it breaks is a {@link BreakActionEvent} with the same actor. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class ExplosionActionEvent extends ActionEvent<ExplosionContext> {

    private ExplosionActionEvent(ExplosionContext context) {
        super(EventSource.EXPLOSION, context);
    }

    /** Before it goes off, after the regions allowed it: cancelling it cancels the explosion - nothing breaks. */
    public static final class Pre extends ExplosionActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(ExplosionContext context) {
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

    /** After it went off. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends ExplosionActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ExplosionContext context) {
            super(context);
        }
    }
}
