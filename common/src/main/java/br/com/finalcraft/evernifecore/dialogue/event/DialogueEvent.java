package br.com.finalcraft.evernifecore.dialogue.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.dialogue.Dialogue;
import br.com.finalcraft.evernifecore.dialogue.DialogueSession;
import br.com.finalcraft.evernifecore.logic.Caller;

import java.util.Objects;

/** Something happened in one player's dialogue. Posted only when somebody listens, on the thread that caused it. */
public abstract class DialogueEvent extends ECEvent implements IECEvent {

    private final DialogueSession session;

    protected DialogueEvent(DialogueSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public DialogueSession getSession() {
        return session;
    }

    public FPlayer getPlayer() {
        return session.getPlayer();
    }

    public Dialogue getDialogue() {
        return session.getDialogue();
    }

    /** Who started the session: the plugin and the origin (an NPC, a region, an effect, a command). */
    public Caller getCaller() {
        return session.getCaller();
    }
}
