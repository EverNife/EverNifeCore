package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.XpContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * Experience a player picked up; the context's amount is the points gained. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class XpGainActionEvent extends ActionEvent<XpContext> {

    private XpGainActionEvent(XpContext context) {
        super(EventSource.XP_GAIN, context);
    }

    /** No platform raises it today: experience picked up cannot be cancelled - {@link br.com.finalcraft.evernifecore.action.ActionSupport#hasPre} answers false. */
    public static final class Pre extends XpGainActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(XpContext context) {
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

    /** After the experience was gained. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends XpGainActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(XpContext context) {
            super(context);
        }
    }
}
