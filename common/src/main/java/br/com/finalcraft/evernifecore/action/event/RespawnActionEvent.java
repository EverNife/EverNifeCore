package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A player respawning after death, asked where they died. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class RespawnActionEvent extends ActionEvent<SimpleContext> {

    private RespawnActionEvent(SimpleContext context) {
        super(EventSource.RESPAWN, context);
    }

    /** Before the respawn, where the platform can cancel one ({@code ActionSupport.hasPre}). */
    public static final class Pre extends RespawnActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(SimpleContext context) {
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

    /** After the respawn point was settled. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends RespawnActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(SimpleContext context) {
            super(context);
        }
    }
}
