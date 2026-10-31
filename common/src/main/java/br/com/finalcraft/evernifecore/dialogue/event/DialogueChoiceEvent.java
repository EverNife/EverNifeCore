package br.com.finalcraft.evernifecore.dialogue.event;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.dialogue.DialogueNode;
import br.com.finalcraft.evernifecore.dialogue.DialogueOption;
import br.com.finalcraft.evernifecore.dialogue.DialogueSession;

import java.util.Objects;

/**
 * A player picked an option, before its gate is paid. Cancelling it charges nothing, runs nothing and leaves the
 * player on the same node.
 */
public class DialogueChoiceEvent extends DialogueEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(DialogueChoiceEvent.class);
    }

    private final DialogueNode node;
    private final DialogueOption option;
    private volatile boolean cancelled;

    public DialogueChoiceEvent(DialogueSession session, DialogueNode node, DialogueOption option) {
        super(session);
        this.node = Objects.requireNonNull(node, "node");
        this.option = Objects.requireNonNull(option, "option");
    }

    /** The node the option was picked on. */
    public DialogueNode getNode() {
        return node;
    }

    public DialogueOption getOption() {
        return option;
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
