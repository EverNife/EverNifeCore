package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.CommandContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

/**
 * A player about to run a command, from the platform's own command event or, where there is none, from the core's command framework. Subscribe to this class to hear both halves, to {@link Pre} or {@link Post} for one of them.
 */
public abstract class CommandActionEvent extends ActionEvent<CommandContext> {

    private CommandActionEvent(CommandContext context) {
        super(EventSource.COMMAND, context);
    }

    /** Before the command runs, after the regions allowed it: cancelling it keeps the command from running. */
    public static final class Pre extends CommandActionEvent implements ECCancellable {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Pre.class);
        }

        private boolean cancelled;

        public Pre(CommandContext context) {
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

    /** After the command ran. Never cancellable, never posted for a cancelled native event. */
    public static final class Post extends CommandActionEvent {

        /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(Post.class);
        }

        public Post(CommandContext context) {
            super(context);
        }
    }
}
