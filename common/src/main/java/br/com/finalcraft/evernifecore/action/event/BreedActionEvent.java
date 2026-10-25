package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * Two animals breeding; the context's entity is the newborn and {@link #getActor()} whoever fed them. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class BreedActionEvent extends ActionEvent<EntityContext> {

    private BreedActionEvent(EntityContext context) {
        super(EventSource.BREED, context);
    }

    /** Before the newborn appears, after the regions allowed it: cancelling it cancels the breeding. */
    public static final class Pre extends BreedActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(EntityContext context) {
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

    /** After the newborn appeared. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends BreedActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(EntityContext context) {
            super(context);
        }
    }
}
