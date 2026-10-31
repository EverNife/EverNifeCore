package br.com.finalcraft.evernifecore.quest.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;

/** An objective reached its goal. */
public class ObjectiveCompleteEvent extends QuestEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ObjectiveCompleteEvent.class);
    }

    private final String objectiveId;

    public ObjectiveCompleteEvent(QuestEngine engine, QuestId questId, ProgressOwner owner, Caller caller, QuestState state, String objectiveId) {
        super(engine, questId, owner, caller, state);
        this.objectiveId = objectiveId;
    }

    /** The objective that completed. */
    public String getObjectiveId() {
        return objectiveId;
    }
}
