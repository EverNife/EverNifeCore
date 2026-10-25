package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * Something hurt: {@link #getActor()} is the attacker (its {@code getRootPlayer()} the pet's owner or the shooter),
 * {@code getContext().getVictim()} the victim. Subscribe to this class to hear both halves, to {@link Pre} or
 * {@link Post} for one of them.
 */
public abstract class DamageActionEvent extends ActionEvent<DamageContext> {

    private DamageActionEvent(DamageContext context) {
        super(EventSource.DAMAGE, context);
    }

    /** Before the hit lands, after the regions allowed it: cancelling it cancels the native event. */
    public static final class Pre extends DamageActionEvent implements ECCancellable {

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

    /** After the hit landed. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends DamageActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(DamageContext context) {
            super(context);
        }
    }
}
