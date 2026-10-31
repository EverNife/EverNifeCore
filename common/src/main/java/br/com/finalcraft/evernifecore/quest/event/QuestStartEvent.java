package br.com.finalcraft.evernifecore.quest.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;

/** The quest started: its gate was paid and its first stage is active. */
public class QuestStartEvent extends QuestEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(QuestStartEvent.class);
    }

    public QuestStartEvent(QuestEngine engine, QuestId questId, ProgressOwner owner, Caller caller, QuestState state) {
        super(engine, questId, owner, caller, state);
    }
}
