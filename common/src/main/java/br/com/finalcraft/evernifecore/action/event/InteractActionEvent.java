package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A block or an entity used by a player. The context is a {@link br.com.finalcraft.evernifecore.action.BlockContext} or an
 * {@link br.com.finalcraft.evernifecore.action.EntityContext}: {@link ActionContext#as} reaches the one it is. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class InteractActionEvent extends ActionEvent<ActionContext> {

    private InteractActionEvent(ActionContext context) {
        super(EventSource.INTERACT, context);
    }

    /** Before the use, after the regions allowed it: cancelling it cancels the native event. */
    public static final class Pre extends InteractActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(ActionContext context) {
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

    /** After the use went through. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends InteractActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(ActionContext context) {
            super(context);
        }
    }
}
