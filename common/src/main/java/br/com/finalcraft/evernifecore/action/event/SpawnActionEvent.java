package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A creature spawned; {@code getContext().getReason()} is the platform's own reason - an egg's included, which the
 * regions never decide. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class SpawnActionEvent extends ActionEvent<SpawnContext> {

    private SpawnActionEvent(SpawnContext context) {
        super(EventSource.SPAWN, context);
    }

    /** Before it appears: cancelling it keeps it from spawning. */
    public static final class Pre extends SpawnActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(SpawnContext context) {
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

    /** After it appeared. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends SpawnActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(SpawnContext context) {
            super(context);
        }
    }
}
