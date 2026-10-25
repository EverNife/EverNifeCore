package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A creature or a player killed: {@link #getActor()} is the killer - the environment for a fall or a fire - and the context's victim the dead. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class KillActionEvent extends ActionEvent<DamageContext> {

    private KillActionEvent(DamageContext context) {
        super(EventSource.KILL, context);
    }

    /** No platform raises it today: a death cannot be cancelled - {@link br.com.finalcraft.evernifecore.action.ActionSupport#hasPre} answers false. */
    public static final class Pre extends KillActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(DamageContext context) {
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

    /** After the death. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends KillActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(DamageContext context) {
            super(context);
        }
    }
}
