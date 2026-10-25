package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * An animal sheared by a player; the context's entity is the animal. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class ShearActionEvent extends ActionEvent<EntityContext> {

    private ShearActionEvent(EntityContext context) {
        super(EventSource.SHEAR, context);
    }

    /** Before the shearing, after the regions allowed it: cancelling it leaves the animal as it was. */
    public static final class Pre extends ShearActionEvent implements ECCancellable {

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

    /** After the animal was sheared. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends ShearActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(EntityContext context) {
            super(context);
        }
    }
}
