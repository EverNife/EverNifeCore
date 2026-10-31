package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A player using an NPC: {@link EntityContext#getEntity()} is the NPC, its {@code getNpc()} the reference a provider
 * recognised it by, {@link EntityContext#getItemInHandKey()} what the player held out. Posted by the NPC providers from
 * their plugin's click event, so a packet NPC with no server entity is heard too. Subscribe to this class to hear both
 * halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class InteractNpcActionEvent extends ActionEvent<EntityContext> {

    private InteractNpcActionEvent(EntityContext context) {
        super(EventSource.INTERACT_NPC, context);
    }

    /** Before the use, after the regions allowed it: cancelling it cancels the plugin's click. */
    public static final class Pre extends InteractNpcActionEvent implements ECCancellable {

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

    /** After the use went through. Never cancellable, never posted for a cancelled click. */
    public static final class Post extends InteractNpcActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(EntityContext context) {
            super(context);
        }
    }
}
