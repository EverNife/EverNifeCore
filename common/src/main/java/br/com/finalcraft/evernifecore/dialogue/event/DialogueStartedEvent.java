package br.com.finalcraft.evernifecore.dialogue.event;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.dialogue.DialogueSession;

/**
 * A dialogue is about to start for a player: the session exists but nothing is shown yet, and a session the player
 * was already in is still open. Cancelling it starts nothing - {@code Dialogues.start} answers null.
 */
public class DialogueStartedEvent extends DialogueEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(DialogueStartedEvent.class);
    }

    private volatile boolean cancelled;

    public DialogueStartedEvent(DialogueSession session) {
        super(session);
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
