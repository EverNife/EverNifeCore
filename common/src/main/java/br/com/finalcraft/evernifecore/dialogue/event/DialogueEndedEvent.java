package br.com.finalcraft.evernifecore.dialogue.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.dialogue.DialogueEndCause;
import br.com.finalcraft.evernifecore.dialogue.DialogueSession;

import java.util.Objects;

/** A dialogue session ended, once, with the first cause that reached it. Never cancellable. */
public class DialogueEndedEvent extends DialogueEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(DialogueEndedEvent.class);
    }

    private final DialogueEndCause cause;

    public DialogueEndedEvent(DialogueSession session, DialogueEndCause cause) {
        super(session);
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    public DialogueEndCause getCause() {
        return cause;
    }
}
